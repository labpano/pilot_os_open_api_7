package com.pi.pano;

import java.util.concurrent.atomic.AtomicInteger;

class PiPanoConfig {
    /**
     * 日志TAG(前缀)
     */
    public static final String TAG_PREFIX = "PilotSDK";
    public static final AtomicInteger S_COUNT = new AtomicInteger(0);

    private PiPanoConfig() {
        // 常量类，不能继承
    }
}
