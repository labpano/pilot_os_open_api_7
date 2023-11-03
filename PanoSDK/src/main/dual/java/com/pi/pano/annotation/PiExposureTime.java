package com.pi.pano.annotation;

import androidx.annotation.IntDef;

/**
 * 曝光时间（Exposed Time），
 * 多少分之一微妙
 */
@IntDef({
        PiExposureTime.auto,
        PiExposureTime._3200,
        PiExposureTime._2000,
        PiExposureTime._1000,
        PiExposureTime._500,
        PiExposureTime._250,
        PiExposureTime._120,
        PiExposureTime._100,
        PiExposureTime._60,
        PiExposureTime._15,
        PiExposureTime._5
})
public @interface PiExposureTime {
    int auto = 0;
    int _3200 = 3200;
    int _2000 = 2000;
    int _1000 = 1000;
    int _500 = 500;
    int _250 = 250;
    int _120 = 120;
    int _100 = 100;
    int _60 = 60;
    int _15 = 15;
    int _5 = 5;
}
