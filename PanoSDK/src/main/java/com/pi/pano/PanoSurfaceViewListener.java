package com.pi.pano;

import com.pi.pano.annotation.PiPreviewMode;

/**
 * PanoSurfaceViewListener控件事件回调
 */
public interface PanoSurfaceViewListener {
    void onPanoSurfaceViewCreate();

    void onPanoSurfaceViewRelease();

    /**
     * 预览模式变化事件
     *
     * @param mode {@link PiPreviewMode}
     */
    void onPanoModeChange(@PiPreviewMode int mode);

    /**
     * 单击MMediaPlayerSurface销毁的时候时的回调
     */
    void onSingleTapConfirmed();

    void onEncodeFrame(int count);
}
