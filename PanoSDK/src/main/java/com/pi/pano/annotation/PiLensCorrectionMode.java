package com.pi.pano.annotation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 镜头畸变矫正模式
 */
@IntDef({
        PiLensCorrectionMode.STITCHED_2,
        PiLensCorrectionMode.FISH_EYE,
        PiLensCorrectionMode.FISH_EYE_HOR,
        PiLensCorrectionMode.FISH_EYE_CELL,
        PiLensCorrectionMode.PANO_MODE_2,
        PiLensCorrectionMode.PANO_MODE_4,
        PiLensCorrectionMode.PLANE_MODE_0,
        PiLensCorrectionMode.PLANE_MODE_1
})
public @interface PiLensCorrectionMode {
    /**
     * 拼接后文件矫正--双目
     */
    int STITCHED_2 = 0x2;
    /**
     * 鱼眼模式
     */
    int FISH_EYE = 0x03;
    /**
     * 鱼眼模式畸变矫正,水平排列--4目
     */
    int FISH_EYE_HOR = 0x3333;
    /**
     * 鱼眼模式畸变矫正,田字格排列--4目
     */
    int FISH_EYE_CELL = 0x5555;
    /**
     * 球面矩形投影模式--双目
     */
    int PANO_MODE_2 = 0x11;
    /**
     * 球面矩形投影模式--4目镜头
     */
    int PANO_MODE_4 = 0x1111;
    /**
     * 平面展开-0号镜头
     */
    int PLANE_MODE_0 = 0x09;
    /**
     * 平面展开-1号镜头
     */
    int PLANE_MODE_1 = 0x90;
}