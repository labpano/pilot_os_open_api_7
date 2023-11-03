package com.pi.pano;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.DngCreator;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.pi.pano.annotation.PiFileStitchFlag;
import com.pi.pano.annotation.PiPhotoFileFormat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

class ImageProcess implements ImageReader.OnImageAvailableListener {
    protected static final AtomicInteger sThreadCount = new AtomicInteger(0);

    protected String mTag = "ImageProcess";

    protected TakePhotoListener mTakePhotoListener;
    protected PhotoParams mParams;

    protected HandlerThread mHandlerThread;
    public Handler mHandler;

    /**
     * 来源图像数据格式
     */
    protected final int mImageFormat;
    protected DngCreator mDngCreator;

    private PiPano mPiPano;

    public void setDngCreator(DngCreator dngCreator) {
        mDngCreator = dngCreator;
    }

    /**
     * 生成dng格式
     */
    public boolean needDng() {
        if (mImageFormat == ImageFormat.RAW_SENSOR) {
            return PiPhotoFileFormat.jpg_dng.equals(mParams.fileFormat);
        }
        return false;
    }

    /**
     * @param takePhotoListener 监听
     * @param imageFormat       数据格式
     * @param async             是否异步
     */
    ImageProcess(TakePhotoListener takePhotoListener, int imageFormat, boolean async, PiPano piPano) {
        mPiPano = piPano;
        mImageFormat = imageFormat;
        if (mImageFormat != ImageFormat.RAW_SENSOR && mImageFormat != ImageFormat.JPEG) {
            throw new RuntimeException("Unsupported ImageFormat: 0x" + Integer.toHexString(mImageFormat));
        }
        mParams = takePhotoListener.mParams;
        mTakePhotoListener = takePhotoListener;
        mTag = "ImageProcess[0x" + Integer.toHexString(mImageFormat) + "]<" + sThreadCount.getAndIncrement() + ">";
        if (async) {
            initThread();
        }
    }

    /**
     * @param takePhotoListener 监听
     * @param imageReader       将关联的ImageReader
     * @param binding           是否绑定 ImageReader，完成拍照后将close ImageReader
     */
    ImageProcess(TakePhotoListener takePhotoListener, ImageReader imageReader, boolean binding, PiPano piPano) {
        this(takePhotoListener, imageReader.getImageFormat(), true, piPano);
        if (binding) {
            bind(imageReader);
        } else {
            prepare(imageReader);
        }
    }

    protected void initThread() {
        Log.d(mTag, "initThread");
        mHandlerThread = new HandlerThread(mTag);
        mHandlerThread.start();
    }

    Handler getHandler() {
        if (null != mHandlerThread && mHandler == null) {
            mHandler = new Handler(mHandlerThread.getLooper());
            return mHandler;
        }
        return mHandler;
    }

    protected ImageReader mImageReader;
    protected boolean isBind = false;

    protected void prepare(ImageReader imageReader) {
        if (mImageFormat != imageReader.getImageFormat()) {
            throw new RuntimeException("Error ImageFormat of ImageReader,0x" + Integer.toHexString(imageReader.getImageFormat()));
        }
        mImageReader = imageReader;
        imageReader.setOnImageAvailableListener(this, getHandler());
        Log.d(mTag, "prepare");
    }

    protected void bind(ImageReader imageReader) {
        prepare(imageReader);
        isBind = true;
    }

    void aborted() {
        Log.d(mTag, "aborted");
        release();
    }

    void release() {
        Log.d(mTag, "release");
        if (null != mHandler && isBind) {
            mHandler.postDelayed(this::releaseImpl, 100);
        } else {
            releaseImpl();
        }
    }

    protected void releaseImpl() {
        if (null != mHandlerThread) {
            mHandlerThread.getLooper().quitSafely();
            mHandlerThread = null;
        }
        mHandler = null;
        if (null != mImageReader) {
            if (isBind) {
                mImageReader.close();
                Log.d(mTag, "imageReader close");
            } else {
                mImageReader.setOnImageAvailableListener(null, null);
                Log.d(mTag, "imageReader disconnect");
            }
            mImageReader = null;
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Log.d(mTag, "onImageAvailable");
        final Image image = reader.acquireLatestImage();
        if (null == image) {
            Log.e(mTag, "onImageAvailable image is null!");
            return;
        }
        handleImage(image);
        release();
    }

    protected void handleImage(Image image) {
        final long beginTimestamp = System.currentTimeMillis();
        Log.d(mTag, "image width: " + image.getWidth() + " height: " + image.getHeight());
        try {
            if (mImageFormat == ImageFormat.RAW_SENSOR) {
                if (needDng()) {
                    saveDng(image);
                } else {
                    saveRaw(image);
                }
            } else if (mImageFormat == ImageFormat.JPEG) {
                saveJpeg(image);
            } else {
                throw new RuntimeException("Unsupported ImageFormat: 0x" + Integer.toHexString(mImageFormat));
            }
        } finally {
            image.close();
        }
        Log.i(mTag, "take photo cost time: " + (System.currentTimeMillis() - beginTimestamp) + "ms");
    }

    protected void notifyTakePhotoComplete(int errorCode) {
        Log.d(mTag, "notifyTakePhotoComplete,errorCode:" + errorCode);
        mTakePhotoListener.dispatchTakePhotoComplete(errorCode);
    }

    /**
     * 保存为dng格式
     */
    protected void saveDng(Image image) {
        int errorCode = 0;
        File unStitchFile = new File(mParams.unStitchDirPath,
                mTakePhotoListener.mParams.obtainBasicName() + PiFileStitchFlag.unstitch + ".dng");
        checkAndCreateParentDir(unStitchFile);
        mTakePhotoListener.mUnStitchFile = unStitchFile;
        int sleepRetry = 30;
        while (mDngCreator == null && sleepRetry > 0) {
            SystemClock.sleep(100);
            sleepRetry--;
        }
        if (mDngCreator != null) {
            try (FileOutputStream output = new FileOutputStream(unStitchFile)) {
                mDngCreator.writeImage(output, image);
            } catch (IOException ex) {
                ex.printStackTrace();
                errorCode = 400;
            }
        } else {
            errorCode = 400;
        }
        Log.d(mTag, "saveDng unStitchFile:" + unStitchFile + ",errorCode:" + errorCode);
        notifyTakePhotoComplete(errorCode);
    }

    /**
     * 保存为raw格式
     */
    protected void saveRaw(Image image) {
        int errorCode = 0;
        File unStitchFile = new File(mParams.unStitchDirPath, mTakePhotoListener.mParams.obtainBasicName() + PiFileStitchFlag.unstitch + ".raw");
        checkAndCreateParentDir(unStitchFile);
        mTakePhotoListener.mUnStitchFile = unStitchFile;
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(unStitchFile))) {
            byte[] byteBuffer = new byte[buffer.remaining()];
            buffer.get(byteBuffer);
            output.write(byteBuffer);
        } catch (IOException ex) {
            ex.printStackTrace();
            errorCode = 400;
        }
        Log.d(mTag, "saveRaw unStitchFile:" + unStitchFile + ",errorCode:" + errorCode);
        notifyTakePhotoComplete(errorCode);
    }

    /**
     * 保存为jpg格式
     */
    protected void saveJpeg(Image image) {
        int errorCode = 0;
        // 普通jpeg
        File unStitchFile = new File(mParams.unStitchDirPath, mTakePhotoListener.mParams.obtainBasicName() + PiFileStitchFlag.unstitch + ".jpg");
        checkAndCreateParentDir(unStitchFile);
        mTakePhotoListener.mUnStitchFile = unStitchFile;
        String path = unStitchFile.getAbsolutePath();
        int ret = directSaveSpatialJpeg(image, path, true);
        if (ret != 0) {
            errorCode = 400;
        }
        Log.d(mTag, "saveJpeg unStitchFile:" + unStitchFile + ",errorCode:" + errorCode);
        notifyTakePhotoComplete(errorCode);
    }

    /**
     * 保存全景照片文件
     *
     * @param image      image data
     * @param filename   文件路径
     * @param appendExif 是否追加exif
     * @return 0:成功
     */
    protected int directSaveSpatialJpeg(Image image, String filename, boolean appendExif) {
        int ret = mPiPano.spatialJpeg(filename, image.getPlanes()[0].getBuffer(), image.getWidth(), image.getHeight(), mParams.heading,
                (System.currentTimeMillis() - SystemClock.elapsedRealtime()) * 1000 + image.getTimestamp() / 1000);
        if (appendExif) {
            appendExif(filename);
        }
        return ret;
    }

    /**
     * 保存数据直接到文件
     */
    protected static void saveBuffer(String filePath, ByteBuffer buffer) {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        saveBuffer(filePath, data);
    }

    /**
     * 保存数据直接到文件
     */
    protected static void saveBuffer(String filePath, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(data);
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void appendExif(String filename) {
        try {
            ExifInterface exifInterface = new ExifInterface(filename);
            exifInterface.setAttribute(ExifInterface.TAG_ARTIST, mParams.artist);
            exifInterface.setAttribute(ExifInterface.TAG_SOFTWARE, mParams.software);
            exifInterface.saveAttributes();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    protected static void checkAndCreateParentDir(File file) {
        File parentFile = file.getParentFile();
        if (null != parentFile && !parentFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentFile.mkdirs();
        }
    }
}
