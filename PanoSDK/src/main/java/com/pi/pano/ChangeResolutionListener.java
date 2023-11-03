package com.pi.pano;

import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiCameraBehavior;
import com.pi.pano.error.PiErrorCode;
import com.pi.pano.wrap.ProParams;

/**
 * 切换分辨率监听接口
 */
public abstract class ChangeResolutionListener {
    public int mWidth;
    public int mHeight;
    public int mFps;
    public String mCameraId;

    public ProParams mProParams;

    /**
     * 强制切换分辨率
     */
    public boolean forceChange = false;

    /**
     * 填充参数
     *
     * @param cameraId 镜头id
     * @param width    宽度
     * @param height   高度
     * @param fps      帧率
     */
    public void fillParams(@Nullable String cameraId, int width, int height, int fps) {
        this.mWidth = width;
        this.mHeight = height;
        this.mFps = fps;
        if (cameraId != null) {
            this.mCameraId = cameraId;
        } else {
            this.mCameraId = "2";
        }
    }

    /**
     * 切换成功
     *
     * @param behavior 针对当前切换分辨率 camera所执行的操作
     */
    protected void onSuccess(@PiCameraBehavior int behavior) {

    }

    /**
     * 切换失败
     *
     * @param code    1、{@link PiErrorCode#CAMERA_NOT_OPENED}
     *                2、{@link PiErrorCode#}
     * @param message 错误信息
     */
    protected void onError(@PiErrorCode int code, String message) {

    }

    public String toParamsString() {
        return "{" +
                "mWidth=" + mWidth +
                ", mHeight=" + mHeight +
                ", mFps=" + mFps +
                ", mCameraId='" + mCameraId + '\'' +
                ", forceChange=" + forceChange +
                '}';
    }
}
