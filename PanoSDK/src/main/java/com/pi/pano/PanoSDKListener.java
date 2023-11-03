package com.pi.pano;

import com.pi.pano.annotation.PiPreviewMode;

/**
 * PanoSDK事件监听。
 * 避免直接做耗时操作，监听调用不一定在ui线程。
 */
public interface PanoSDKListener {
    /**
     * SDK 已创建。
     * 已初始化完成，可正常使用预览相关功能。
     */
    void onPanoCreate();

    /**
     * SDK 已释放。
     * 释放后不应再使用预览（数据）及操作。
     */
    void onPanoRelease();

    /**
     * 预览模式发生变化
     */
    void onChangePreviewMode(@PiPreviewMode int mode);

    /**
     * 预览被单击
     */
    void onSingleTap();

    /**
     * 帧率
     * (当前定时触发此回调)
     */
    void onEncodeFrame(int count);
}
