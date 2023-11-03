package com.pi.pano.annotation;

/**
 * 推流分辨率
 */
public @interface PiPushResolution {
    /**
     * 8k,四路流
     */
    String _8K = "3648*2280";
    /**
     * 8k,单路流
     */
    String _8K_2 = "3648*2280#2";
    /**
     * 6K-未拼接
     */
    String _6K = PiResolution._5_7K;
    /**
     * 4K-包含帧率优先
     */
    String _4K = PiResolution._4K;
    /**
     * 4K-画质优先，降低帧率
     */
    String _4K_QUALITY = PiResolution._4K + "#2";
    /**
     * 高清
     */
    String HIGH = PiResolution._2_5K;
    /**
     * 1080，其码率和 HIGH 一致
     */
    String _1080P = PiResolution._1080P;
    /**
     * 标清
     */
    String STANDARD = PiResolution.STANDARD;
    /**
     * 720,其码率和 STANDARD 一致
     */
    String _720P = PiResolution._720P;
    /**
     * 流畅
     */
    String SMOOTH = PiResolution.SMOOTH;
}
