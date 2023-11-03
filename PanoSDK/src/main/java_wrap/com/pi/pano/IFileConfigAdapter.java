package com.pi.pano;

import androidx.annotation.NonNull;

/**
 * fileConfig生成适配接口
 */
interface IFileConfigAdapter {

    /**
     * 初始化 照片config数据
     *
     * @param photoParams 拍照参数
     */
    void saveConfig(@NonNull PhotoParams photoParams, boolean lensProtected);

    /**
     * 初始化 视频config数据
     *
     * @param videoParams 录像参数
     */
    void saveConfig(@NonNull VideoParams videoParams, boolean lensProtected, float fov);

    class Key {
        public static final String KEY_FITTINGS = "fittings";
        public static final String KEY_FPS = "fps";
        public static final String KEY_BITRATE = "bitrate";
        public static final String KEY_RESOLUTION = "resolution";
        public static final String KEY_SPATIAL_AUDIO = "spatialAudio";
        public static final String KEY_TIME_LAPSE_RATIO = "timelapseRatio";
        public static final String KEY_FIELD_OF_VIEW = "fieldOfView";
        public static final String KEY_HDR_COUNT = "hdrCount";
        public static final String KEY_VERSION = "version";
    }
}
