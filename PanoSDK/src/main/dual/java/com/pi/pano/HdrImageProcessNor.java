package com.pi.pano;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;

import com.pi.pano.annotation.PiFileStitchFlag;
import com.pi.pano.annotation.PiResolution;
import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * hdr拍照处理，一张一张拍摄。
 */
class HdrImageProcessNor extends HdrImageProcess implements Runnable {
    private final PiCallback callback;
    private boolean finished = false;
    private int mErrorCode = -1;
    private boolean mHasAborted = false;

    HdrImageProcessNor(TakePhotoListener takePhotoListener,
                       ImageReader imageReader, PiCallback callback, Handler handler, PiPano piPano) {
        super(takePhotoListener, imageReader, handler, piPano);
        if (!mParams.isHdr()) {
            throw new RuntimeException("TakePhoto isn't hdr");
        }
        this.callback = callback;
        prepare(imageReader);
        new Thread(this).start();
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        Log.i(mTag, "onImageAvailable :" + reader.getMaxImages() + "," + image);
        boolean success = false;
        if (image != null) {
            handleImageCount++;
            handleImage(image);
            callback.onSuccess();
            success = true;
        }
        release();
        if (!success) {
            callback.onError(new PiError(-1));
        }
    }

    public void onCaptureError(PiError error) {
        finished = true;
        if (callback != null) {
            callback.onError(error);
        }
    }

    @Override
    void aborted() {
        Log.d(mTag, "aborted");
        mHasAborted = true;
        if (!finished) {
            finished = true;
        }
        if (null != mHandler) {
            mHandler.postDelayed(this::releaseImpl, 100);
        } else {
            releaseImpl();
        }
    }

    @Override
    protected void releaseImpl() {
//        finished = true;
        super.releaseImpl();
    }

    @Override
    protected void saveJpeg(Image image) {
//        super.saveJpeg(image);
        if (null == mHdrSourceDir) {
            mHdrSourceDir = new File(mParams.unStitchDirPath,
                    "." + mTakePhotoListener.mParams.obtainBasicName() + PiFileStitchFlag.unstitch + ".hdr");
        }
        // hdr index文件
        File indexFile = new File(mHdrSourceDir, (mHdrSourceFileCount++) + ".jpg");
        checkAndCreateParentDir(indexFile);
        boolean append = mHdrSourceFileCount - 1 == mParams.hdrCount / 2;
        Log.i(mTag, "saveJpg :" + indexFile + "," + append);
        directSaveSpatialJpeg(image, indexFile.getAbsolutePath(), append);
        new Thread(() -> {
            String resolution = mParams.resolution;
            ResolutionSize size = ResolutionSize.parseSize(resolution);
            Log.w(mTag, "=======hdr run ==" + indexFile + ",,resolution ==> " + resolution + ",," + size);
            File stitchTempFile = new File(indexFile.getParent(), indexFile.getName().replace(".jpg", "_s.jpg"));
            StitchingOpticalFlow.stitchJpegFile(indexFile.getAbsolutePath(), stitchTempFile.getAbsolutePath(), true, false,
                    99, size.width, size.height);
            Log.d(mTag, "stitch finish" + stitchTempFile + "===>" + stitchTempFile.exists());
            if (stitchTempFile.exists()) {
                hdrFiles.add(stitchTempFile);
            }
            if (append && PiResolution._12K.equals(resolution) && mTakePhotoListener.mParams.createThumb) {
                File finalMiddleFile = indexFile;
                new Thread(() -> thumbFile[0] = ThumbnailGenerator.createImageThumbFile(finalMiddleFile)).start();
            }
        }).start();
        if (mHdrSourceFileCount == mParams.hdrCount) {
            saveJpgFinished.set(true);
        }
    }

    final File[] thumbFile = {null};

    @Override
    public void run() {
        int width = 0;
        int height = 0;
        int bitmapCount = 0;
        ByteBuffer byteBuffer = null;
        File middleFile = null;
        mErrorCode = -1;
        boolean takeThumb = mTakePhotoListener.mParams.createThumb;
        List<File> stitchList = new ArrayList<>();
        while (!finished) {
            File file = null;
            try {
                if (stitchList.size() >= mParams.hdrCount) {
                    finished = true;
                    break;
                }
                file = hdrFiles.takeFirst();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (file == null || finished) {
                finished = true;
                break;
            }
            stitchList.add(file);
        }
        if (stitchList.size() == mParams.hdrCount) {
            for (int i = 0; i < stitchList.size(); i++) {
                File file = stitchList.get(i);
                Log.i(mTag, "takeFirst :" + file + "===>" + middleFile + "," + file.exists());
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), null);
                if (bitmap == null) {
                    Log.e(mTag, "stackHdrFile,decodeFile null,file:" + file);
                    mErrorCode = PiErrorCode.HDR_PHOTO_STACK_BITMAP;
                    finished = true;
                    break;
                }
                if (bitmapCount == 0) {
                    width = bitmap.getWidth();
                    height = bitmap.getHeight();
                    byteBuffer = ByteBuffer.allocateDirect(width * height * 4);
                }
                bitmap.copyPixelsToBuffer(byteBuffer);
                bitmap.recycle();
                int ret = PiPano.nativeHdrAddImage(byteBuffer, null, width, height, width);
                Log.d(mTag, "nativeHdrAddImage result:" + ret + ", " + bitmapCount + ",," + mParams.hdrCount);
                if (ret != 0) {
                    finished = true;
                    Log.d(mTag, "nativeHdrAddImage error finished=true :" + ret + ", " + bitmapCount + ",," + mParams.hdrCount);
                    mErrorCode = PiErrorCode.HDR_PHOTO_STACK_FAILED;
                    break;
                }
                if (bitmapCount == mParams.hdrCount / 2) {
                    middleFile = file;
                }
                byteBuffer.clear();
                bitmapCount++;
                if (bitmapCount == mParams.hdrCount) {
                    mErrorCode = 0;
                    byteBuffer = null;
                }
            }
        }
        if (mErrorCode == 0 && width > 0) {
            File unstitchFile = new File(mParams.unStitchDirPath,
                    mTakePhotoListener.mParams.obtainBasicName() + PiFileStitchFlag.stitch + "_hdr" + ".jpg");
            mTakePhotoListener.mUnStitchFile = unstitchFile;
            int result = PiPano.nativeHdrCalculate(unstitchFile.getAbsolutePath(),
                    middleFile.getAbsolutePath(), width, height, width);
            Log.i(mTag, "nativeHdrCalculate result :" + result);
            if (result != 0) {
                Log.e(mTag, "stackHdrFile, nativeHdrCalculate err : " + result);
                mErrorCode = result;
                return;
            }
            if (takeThumb) {
                if (PiResolution._12K.equals(mParams.resolution)) {
                    if (thumbFile[0] != null) {
                        int code = PiPanoUtils.injectThumbnail(unstitchFile.getAbsoluteFile(),
                                thumbFile[0].getAbsoluteFile());
                        if (code != 0) {
                            Log.d(mTag, "injectThumbnail error :" + code);
                        }
                    }
                } else {
                    ThumbnailGenerator.injectExifThumbnailForImage(unstitchFile);
                }
            }
        } else {
            PiPano.clearImageList();
        }
        deleteFile(thumbFile[0]);
        if (mHasAborted) {
            deleteFile(mHdrSourceDir);
            return;
        }
        if (mParams.isSaveHdrSourceFile()) {
            File hdr = new File(mHdrSourceDir.getParentFile(),
                    mHdrSourceDir.getName().substring(1));
            mHdrSourceDir.renameTo(hdr);
            mHdrSourceDir = hdr;
        } else {
            deleteFile(mHdrSourceDir);
            mHdrSourceDir = null;
        }
        callback.onSuccess();
    }

}
