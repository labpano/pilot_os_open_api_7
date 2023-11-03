package com.pi.pano.annotation;

/**
 * 分辨率
 */
public @interface PiResolution {
    /**
     * 12K
     */
    String _12K = "11968*5984";
    /**
     * 12k 使用的camera出帧分辨率
     */
    String _12K_c = "10920*5460";
    /**
     * 8K
     */
    String _8K = "7680*3840";
    /**
     * 5.7K
     */
    String _5_7K = "5760*2880";
    /**
     * 5.7K 使用的camera出帧分辨率
     */
    String _5_7K_c = "5800*2900";
    /**
     * 4K
     */
    String _4K = "3840*1920";
    /**
     * 高清
     */
    String _2_5K = "2560*1280";
    /**
     * 1080P
     */
    String _1080P = "2160*1080";
    /**
     * 非全景，9：16
     */
    String _1080P2 = "1080*1920";
    /**
     * 非全景，16：9
     */
    String _1080P3 = "1920*1080";
    /**
     * 标清
     */
    String STANDARD = "1920*960";
    /**
     * 720P
     */
    String _720P = "1440*720";
    /**
     * 流畅
     */
    String SMOOTH = "1280*640";
}
