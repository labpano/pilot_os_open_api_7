package com.pi.pano.annotation;


import androidx.annotation.IntDef;

@IntDef({
        PiCameraBehavior.OPEN,
        PiCameraBehavior.SWITCH,
        PiCameraBehavior.START_PREVIEW,
        PiCameraBehavior.UPDATE_PREVIEW
})
public @interface PiCameraBehavior {

    /**
     * 重新打开camera,包含{@link PiCameraBehavior#SWITCH}的情况
     */
    int OPEN = 0;
    /**
     * 切换camera[cameraId变化，帧率变化]
     */
    int SWITCH = 1;
    /**
     * 重新开启预览[单纯分辨率变化，强制切换，]
     */
    int START_PREVIEW = 2;
    /**
     * 更新预览
     */
    int UPDATE_PREVIEW = 3;
}
