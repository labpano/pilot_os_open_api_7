package com.pi.pano.wrap.annotation;

import androidx.annotation.StringDef;

/**
 * 街景视频帧率
 */
@StringDef({
        PiFps._0_3,
        PiFps._1,
        PiFps._2,
        PiFps._3,
        PiFps._4,
        PiFps._5,
        PiFps._7,
})
public @interface PiStreetVideoFps {
}
