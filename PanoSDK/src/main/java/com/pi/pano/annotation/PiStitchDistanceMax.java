package com.pi.pano.annotation;

/**
 * 拼接距离最大值，
 * 根据设备型号不同有区别
 */
public @interface PiStitchDistanceMax {
    /**
     * era 设备上拼接距离最大值
     */
    float eraDevice = 1.35f;
    /**
     * one 设备上拼接距离最大值
     */
    float oneDevice = 0.9f;
    /**
     * lock/insight 设备上拼接距离最大值
     */
    float lockOrInsightDevice = 1.45f;

    /**
     * wide 设备上拼接距离最大值
     */
    float wide = 5.0f;
}
