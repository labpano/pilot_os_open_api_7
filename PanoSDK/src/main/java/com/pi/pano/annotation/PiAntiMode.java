package com.pi.pano.annotation;

import androidx.annotation.StringDef;

/**
 * 抗频闪
 */
@StringDef({
        PiAntiMode.off,
        PiAntiMode._50Hz,
        PiAntiMode._60Hz,
        PiAntiMode.auto
})
public @interface PiAntiMode {

    /**
     * 关闭
     */
    String off = "off";

    String _50Hz = "50Hz";

    String _60Hz = "60Hz";
    /**
     * 自动
     */
    String auto = "auto";
}