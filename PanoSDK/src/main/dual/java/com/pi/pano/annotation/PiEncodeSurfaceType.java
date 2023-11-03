package com.pi.pano.annotation;

import androidx.annotation.IntDef;

/**
 * 编码接收的 inputSurface 类型
 */
@IntDef
public @interface PiEncodeSurfaceType {
    /**
     * 预览
     */
    int PREVIEW = 0;
    /**
     * 全景视频
     */
    int RECORD = 1;
    /**
     * 全景直播
     */
    int LIVE = 2;
    /**
     * 投屏演示
     */
    int PRESENTATION = 3;
    /**
     * 低清縮略小视频(640*320P)
     */
    int RECORD_THUMB = 4;
    /**
     * 屏幕直播/屏幕视频
     */
    int SCREEN_CAPTURE = 5;
    /**
     * 预览-vio手机控制
     */
    int PREVIEW_VIO = 6;
    /**
     * 当前屏幕展示的照片
     */
    int SCREEN_PHOTO = 7;
    /**
     * 延时摄影录像0镜头
     */
    int RECORD_TIME_LAPSE_0 = 8;
    /**
     * 延时摄影录像1镜头
     */
    int RECORD_TIME_LAPSE_1 = 9;
}