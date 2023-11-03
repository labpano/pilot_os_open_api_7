package com.pi.pano.annotation;

import androidx.annotation.StringDef;

/**
 * 视频分辨率
 */
@StringDef({
        PiResolution._8K,
        PiResolution._5_7K,
        PiResolution._4K,
        PiResolution._2_5K,
})
public @interface PiVideoResolution {
}
