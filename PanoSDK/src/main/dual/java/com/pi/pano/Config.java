package com.pi.pano;

public class Config {
    public static final int CAMERA_COUNT = 1;
    public static final String PIPANO_SO_NAME = "PiPanoDual";

    /**
     * 多画面出帧同步时，判断同步的差值，小于此值为同步
     */
    static final int FRAME_SYNC_DELTA_TIME = 5000000;
    /**
     * 释放camera超时时间ms
     */
    static final int MAX_RELEASE_CAMERA_TIMEOUT = 7000;
    /**
     * 停止录像默认超时时间ms
     */
    static final int MAX_STOP_VIDEO_TIMEOUT = 8000;

    /**
     * 防抖，默认开。
     */
    public static final String PERSIST_DEV_PANO_USE_GYROSCOPE_ENABLE = "persist.dev.pano.use_gyroscope.enable";
    /**
     * 间隔计算拼接距离，默认开。
     */
    public static final String PERSIST_DEV_PANO_RE_CALI_PARAM_ENABLE = "persist.dev.pano.re_cali_param.enable";
    /**
     * 双码流硬同步，默认开。
     */
    public static final String PERSIST_DEV_PANO_DUAL_FORCE_HW_SYNC_ENABLE = "persist.dev.pano.dual_force_hw_sync.enable";
    /**
     * 录像媒体编码方式，默认2。
     * 1: MediaCodec, {@link  MediaRecorderUtil}
     * 2: MediaRecorder, {@link  MediaRecorderUtil2}
     * 3: MediaCodec by c, {@link  MediaRecorderUtil3};
     */
    public static final String PERSIST_DEV_PANO_MEDIACODE_WAY = "persist.dev.pano.mediacode.type";
    /**
     * 调试模式中--camera释放超时时间配置 ms :默认5000ms
     */
    public static final String PERSIST_DEV_TIMEOUT_CAMERA_RELEASE = "persist.dev.time_out_camera_release";
    /**
     * 调试模式中--停止录像超时时间配置 ms ,默认8000ms
     */
    public static final String PERSIST_DEV_TIMEOUT_STOP_VIDEO = "persist.dev.time_out_stop_video";
}
