package com.pi.pano;

import android.content.res.AssetManager;

import com.pi.pano.annotation.PiSlamState;

/**
 * Slam 监听
 */
public abstract class SlamListener {
    AssetManager assetManager;
    float lenForCalMeasuringScale;
    boolean showPreview;
    boolean useForImuToPanoRotation;

    /**
     * slam跟踪状态变化
     *
     * @param state 0-未初始化 1-正在初始化 2-正在跟踪 3-丢失跟踪
     */
    public void onTrackStateChange(@PiSlamState int state) {
    }

    /**
     * 产生错误
     *
     * @param errorCode 错误码 0-初始化成功,没有错误 大于0表示产生错误
     */
    public void onError(int errorCode) {
    }

    /**
     * 结束slam后,回调此接口,包含拍照的位置数据
     *
     * @param position 包含照片位置数据的字符串
     */
    public void onPhotoPosition(String position) {
    }

    /**
     * slam结束
     *
     * @param accuracy          当slam用于矫正imu和pano旋转关系的时候,这个值代表矫正的准确度
     * @param imuToPanoRotation 表示imu和pano旋转关系的四元数
     */
    public void onStop(float accuracy, float[] imuToPanoRotation) {
    }

    /**
     * 返回debug文件夹
     *
     * @param debugDir debug文件夹
     */
    public void onSetDebugDir(String debugDir) {
    }
}
