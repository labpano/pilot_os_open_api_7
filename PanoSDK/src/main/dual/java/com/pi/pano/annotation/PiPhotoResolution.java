package com.pi.pano.annotation;

import androidx.annotation.StringDef;

/**
 * 拍照分辨率
 */
@StringDef({
        PiResolution._12K,
        PiResolution._8K,
        PiResolution._5_7K,
        PiResolution._4K,
})
public @interface PiPhotoResolution {
}