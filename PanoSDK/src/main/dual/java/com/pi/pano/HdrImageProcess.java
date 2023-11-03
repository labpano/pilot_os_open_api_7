package com.pi.pano;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.pi.pano.annotation.PiFileStitchFlag;
import com.pi.pano.annotation.PiResolution;
import com.pi.pano.error.PiErrorCode;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * hdr拍照处理，
 * 应对 Camera 连拍。
 */
class HdrImageProcess extends ImageProcess {
    protected int handleImageCount = 0;

    protected final AtomicBoolean saveJpgFinished = new AtomicBoolean(false);
    protected LinkedBlockingDeque<File> hdrFiles = new LinkedBlockingDeque();
    protected int mHdrSourceFileCount;
    protected File mHdrSourceDir;

    HdrImageProcess(TakePhotoListener takePhotoListener, ImageReader imageReader, PiPano piPano) {
        super(takePhotoListener, imageReader.getImageFormat(), true, piPano);
        if (!mParams.isHdr()) {
            throw new RuntimeException("TakePhoto isn't hdr");
        }
        prepare(imageReader);
        mHdrSourceFileCount = 0;
    }

    HdrImageProcess(TakePhotoListener takePhotoListener, ImageReader imageReader, Handler handler, PiPano piPano) {
        super(takePhotoListener, imageReader.getImageFormat(), false, piPano);
        if (!mParams.isHdr()) {
            throw new RuntimeException("TakePhoto isn't hdr");
        }
        mHandler = handler;
        prepare(imageReader);
        mHdrSourceFileCount = 0;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        //super.onImageAvailable(reader);
        Log.i(mTag, "onImageAvailable");
        Image image;
        while ((image = reader.acquireNextImage()) != null) {
            handleImageCount++;
            handleImage(image);
        }
        release();
    }

    @Override
    protected void release() {
        if (handleImageCount == mParams.hdrCount) {
            // 非hdr，或hdr拍摄完成
            super.release();
        }
    }

    @Override
    protected void saveJpeg(Image image) {
        if (null == mHdrSourceDir) {
            mHdrSourceDir = new File(mParams.unStitchDirPath, "." + mTakePhotoListener.mParams.obtainBasicName() + PiFileStitchFlag.unstitch + ".hdr");
        }
        // hdr index文件
        File indexFile = new File(mHdrSourceDir, (mHdrSourceFileCount++) + ".jpg");
        checkAndCreateParentDir(indexFile);
        boolean append = mHdrSourceFileCount - 1 == mParams.hdrCount / 2;
        directSaveSpatialJpeg(image, indexFile.getAbsolutePath(), append);
        hdrFiles.add(indexFile);
        String resolution = mParams.resolution;
        if (append && PiResolution._12K.equals(resolution) && mTakePhotoListener.mParams.createThumb) {
            new Thread(() -> thumbFile[0] = ThumbnailGenerator.createImageThumbFile(indexFile)).start();
        }
        if (mHdrSourceFileCount == mParams.hdrCount) {
            saveJpgFinished.set(true);
        }
    }

    final File[] thumbFile = {null};

    public int stackHdrConfigured() {
        Log.d(mTag, "stackHdrConfigured cur hdr count:" + mHdrSourceFileCount);
        int count = 0;
        while (!saveJpgFinished.get()) {
            if (count >= 50) {
                Log.e(mTag, "stackHdrConfigured 3s time out ! " + mHdrSourceFileCount);
                return PiErrorCode.TIME_OUT;
            }
            SystemClock.sleep(100);
            count++;
        }
        return stackHdrFile();
    }

    private int stackHdrFile() {
        int errorCode = -1;
        if (null != mHdrSourceDir) {
            File[] list = mHdrSourceDir.listFiles();
            if (null != list && list.length > 0) {
                int imageCount = list.length;
                Log.d(mTag, "stackHdrFile file :" + imageCount + ",hdr," + mParams.hdrCount);
                if (imageCount < mParams.hdrCount) {
                    return PiErrorCode.HDR_PHOTO_LOSE;
                }
                Arrays.sort(list);
                File unstitchFile = new File(mParams.unStitchDirPath,
                        mTakePhotoListener.mParams.obtainBasicName() + PiFileStitchFlag.unstitch + "_hdr" + ".jpg");
                checkAndCreateParentDir(unstitchFile);
                mTakePhotoListener.mUnStitchFile = unstitchFile;
                int width;
                int height;
                List<File> hdrList = new ArrayList<>();
                for (File file : list) {
                    if (!file.exists() || file.getName().startsWith(".t.")) {
                        continue;
                    }
                    Log.d(mTag, "stackHdrFile,>>>>file:" + file);
                    hdrList.add(file);
                }
                if (hdrList.size() != mParams.hdrCount) {
                    return PiErrorCode.HDR_PHOTO_STACK_BITMAP;
                }
                int[] mSize = new int[]{0, 0};
                errorCode = addHdrImage(hdrList, mSize);
                width = mSize[0];
                height = mSize[1];
                if (errorCode == 0 && width > 0) {
                    // 合成hdr
                    File middleFile = list[list.length / 2];
                    int result = PiPano.nativeHdrCalculate(unstitchFile.getAbsolutePath(),
                            middleFile.getAbsolutePath(), width, height, width);
                    if (result != 0) {
                        Log.e(mTag, "stackHdrFile, nativeHdrCalculate err : " + result);
                        return result;
                    }
                    Log.i(mTag, "nativeHdrCalculate result ：" + result + "==>" + mParams.resolution
                            + ",thumb :" + thumbFile[0]);
                    if (PiResolution._12K.equals(mParams.resolution)) {
                        if (thumbFile[0] != null) {
                            int code = PiPanoUtils.injectThumbnail(unstitchFile.getAbsoluteFile(),
                                    thumbFile[0].getAbsoluteFile());
                            deleteFile(thumbFile[0]);
                            if (code != 0) {
                                Log.e(mTag, "injectThumbnail error :" + code);
                            }
                        }
                    } else {
                        ThumbnailGenerator.injectExifThumbnailForImage(unstitchFile);
                    }
                } else {
                    PiPano.clearImageList();
                }
            }
            if (mParams.isSaveHdrSourceFile()) {
                File hdr = new File(mHdrSourceDir.getParentFile(), mHdrSourceDir.getName().substring(1));
                mHdrSourceDir.renameTo(hdr);
                mHdrSourceDir = hdr;
            } else {
                deleteFile(mHdrSourceDir);
                mHdrSourceDir = null;
            }
        }
        return errorCode;
    }

    static void deleteFile(File file) {
        if (file == null) {
            return;
        }
        if (file.isDirectory()) {
            file.listFiles(pathname -> {
                if (pathname.isDirectory()) {
                    deleteFile(pathname);
                }
                return pathname.delete();
            });
        }
        file.delete();
    }

    private int addHdrImage(List<File> hdrList, int[] mSize) {
        List<Bitmap> bitmapList = new ArrayList<>(mParams.hdrCount);
        bitmapList.add(null);
        bitmapList.add(null);
        bitmapList.add(null);
        AtomicInteger success = new AtomicInteger();
        Thread thread = new Thread(() -> {
            final Object lock = new Object();
            for (int i = 0; i < hdrList.size(); i++) {
                final int localIndex = i;
                new Thread(() -> {
                    File file = hdrList.get(localIndex);
                    Log.d(mTag, "decodeFile ==>" + localIndex + ",," + file);
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), null);
                    success.incrementAndGet();
                    if (bitmap == null) {
                        Log.e(mTag, "stackHdrFile,decodeFile null,file:" + file);
                        return;
                    }
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    mSize[0] = width;
                    mSize[1] = height;
                    bitmapList.set(localIndex, bitmap);
                    if (success.get() >= mParams.hdrCount) {
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    }
                }).start();
            }
            try {
                synchronized (lock) {
                    lock.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(mSize[0] * mSize[1] * 4);
        for (int i = 0; i < bitmapList.size(); i++) {
            Bitmap bitmap = bitmapList.get(i);
            if (bitmap == null) {
                bitmapList.clear();
                return PiErrorCode.HDR_PHOTO_STACK_BITMAP;
            }
            bitmap.copyPixelsToBuffer(byteBuffer);
            bitmap.recycle();
            int ret = PiPano.nativeHdrAddImage(byteBuffer, null, mSize[0], mSize[1], mSize[0]);
            Log.d(mTag, "nativeHdrAddImage result :" + ret + ",," + Arrays.toString(mSize));
            byteBuffer.clear();
            if (ret != 0) {
                return PiErrorCode.HDR_PHOTO_STACK_FAILED;
            }
        }
        bitmapList.clear();
        return 0;
    }
}
