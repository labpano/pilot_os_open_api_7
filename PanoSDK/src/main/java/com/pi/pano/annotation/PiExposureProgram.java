package com.pi.pano.annotation;

import androidx.annotation.IntDef;

/**
 * 曝光时间模式
 */
@IntDef({
        PiExposureProgram.auto,
        PiExposureProgram.manual,
})
public @interface PiExposureProgram {
    /**
     * 自动,未指定，即默认值。
     */
    int auto = 0;
    /**
     * 手动
     */
    int manual = 1;
}
