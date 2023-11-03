package com.pi.pano.wrap.annotation;

import androidx.annotation.IntDef;

/**
 * pano预览类型
 */
@IntDef({
        PiPreviewType.IMAGE,
        PiPreviewType.VIDEO_PLANE,
        PiPreviewType.VIDEO_PANO,
})
public @interface PiPreviewType {

    /**
     * 全景照片
     */
    int IMAGE = 1;
    /**
     * 全景视频 or 直播
     */
    int VIDEO_PANO = 5;
    /**
     * 普通平面视频
     */
    int VIDEO_PLANE = 6;

}
