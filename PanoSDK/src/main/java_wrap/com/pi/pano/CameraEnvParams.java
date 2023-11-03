package com.pi.pano;

import androidx.annotation.NonNull;

/**
 * 集中管理 当前使用的camera环境 参数
 */
public class CameraEnvParams {

    public static final String CAPTURE_PHOTO = "photo";
    public static final String CAPTURE_STREAM = "stream";

    private String mCameraId;
    int mWidth;
    int mHeight;
    int mFps;
    /**
     * 捕获模式， 照片 or 流
     */
    private String mCaptureMode;

    private boolean mLockDefaultPreviewFps;

    public CameraEnvParams(String cameraId, int width, int height, int fps, String captureMode, boolean lockDefaultPreviewFps) {
        mCameraId = cameraId;
        mWidth = width;
        mHeight = height;
        mFps = fps;
        mCaptureMode = captureMode;
        mLockDefaultPreviewFps = lockDefaultPreviewFps;
    }

    public String getCameraId() {
        return mCameraId;
    }

    public void setCameraId(String cameraId) {
        mCameraId = cameraId;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getFps() {
        return mFps;
    }

    public void setFps(int fps) {
        mFps = fps;
    }

    public String getCaptureMode() {
        return mCaptureMode;
    }

    public void setCaptureMode(String captureMode) {
        this.mCaptureMode = captureMode;
    }

    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public boolean isLockDefaultPreviewFps() {
        return mLockDefaultPreviewFps;
    }

    public void setLockDefaultPreviewFps(boolean lockDefaultPreviewFps) {
        mLockDefaultPreviewFps = lockDefaultPreviewFps;
    }

    @NonNull
    @Override
    public String toString() {
        return "CameraEnvParams{" +
                "mCameraId='" + mCameraId + '\'' +
                ", mWidth=" + mWidth +
                ", mHeight=" + mHeight +
                ", mFps=" + mFps +
                ", mCaptureMode='" + mCaptureMode + '\'' +
                '}';
    }
}
