package com.pi.pano.annotation;

import androidx.annotation.IntDef;

/**
 * 感光度,自动曝光时间时使用
 */
@IntDef({
        PiIso.auto
})
public @interface PiIsoInAutoEp {
}
