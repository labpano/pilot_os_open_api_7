package com.pi.pano.annotation;

import androidx.annotation.IntDef;

/**
 * 曝光补偿值（Exposure Compensation）索引值。
 */
@IntDef({
        PiExposureCompensation.reduce_4,
        PiExposureCompensation.reduce_3,
        PiExposureCompensation.reduce_2,
        PiExposureCompensation.reduce_1,
        PiExposureCompensation.normal,
        PiExposureCompensation.enhance_1,
        PiExposureCompensation.enhance_2,
        PiExposureCompensation.enhance_3,
        PiExposureCompensation.enhance_4
})
public @interface PiExposureCompensation {
    /**
     * 减弱，最暗
     */
    int reduce_4 = -4;
    int reduce_3 = -3;
    int reduce_2 = -2;
    int reduce_1 = -1;

    /**
     * 正常
     */
    int normal = 0;

    int enhance_1 = +1;
    int enhance_2 = +2;
    int enhance_3 = +3;
    /**
     * 增强，最亮
     */
    int enhance_4 = +4;
}
