package com.pi.pano.annotation;

/**
 * 摄像头 id
 */
public @interface PiCameraId {
    /**
     * 前置摄像头
     */
    int FRONT = 0;
    /**
     * 后置摄像头
     */
    int BACK = 1;
    /**
     * 双镜头
     */
    int DOUBLE = 2;
    /**
     * 异步同时打开双镜头 {@link PiCameraId#FRONT},{@link PiCameraId#BACK} 双码流
     */
    int ASYNC_DOUBLE = 10;

}
