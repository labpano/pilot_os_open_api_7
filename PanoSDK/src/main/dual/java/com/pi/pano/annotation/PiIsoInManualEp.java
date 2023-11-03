package com.pi.pano.annotation;

import androidx.annotation.IntDef;

/**
 * 感光度,手动曝光时间时使用
 */
@IntDef({
        PiIso._100,
        PiIso._200,
        PiIso._400,
        PiIso._640,
        PiIso._800,
        PiIso._1000,
        PiIso._1250,
        PiIso._1600
})
public @interface PiIsoInManualEp {
}
