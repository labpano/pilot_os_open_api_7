package com.pi.pano.annotation;

import androidx.annotation.StringDef;

/**
 * 白平衡
 */
@StringDef({
        PiWhiteBalance.auto,
        PiWhiteBalance.fluorescent,
        PiWhiteBalance.incandescent,
        PiWhiteBalance.cloudy_daylight,
        PiWhiteBalance.daylight
})
public @interface PiWhiteBalance {
    /**
     * 自动
     */
    String auto = "auto";
    /**
     * 荧光灯
     */
    String fluorescent = "fluorescent";
    /**
     * 白炽灯
     */
    String incandescent = "incandescent";
    /**
     * 阴天
     */
    String cloudy_daylight = "cloudy-daylight";
    /**
     * 日光
     */
    String daylight = "daylight";
}
