package com.pi.pano;

/**
 * 支持的分辨率&帧率。
 */
public @interface CameraPreview {
    /**
     * 单码流，拍照预览分辨率
     */
    int[] CAMERA_PREVIEW_3868_1934_30 = new int[]{3868, 1934, 30};

    /**
     * 双码流，8K-10FPS
     */
    int[] CAMERA_PREVIEW_3868_3868_10 = new int[]{3868, 3868, 30/*需使用30,出10*/};
    /**
     * 双码流，5.7K-30FPS
     */
    int[] CAMERA_PREVIEW_2900_2900_30 = new int[]{2900, 2900, 30};
    int[] CAMERA_PREVIEW_2900_2900_16 = new int[]{2900, 2900, 16};
    int[] CAMERA_PREVIEW_2900_2900_15 = new int[]{2900, 2900, 15};
    int[] CAMERA_PREVIEW_2900_2900_14 = new int[]{2900, 2900, 14};
    /**
     * 双码流，4K-60FPS
     */
    int[] CAMERA_PREVIEW_1932_1932_60 = new int[]{1932, 1932, 60};

    /**
     * 双码流，8K-30FPS，虚拟.
     * 真实出帧，仅10fps
     */
    int[] CAMERA_PREVIEW_980_980_10_v = new int[]{980, 980, 30/*需使用30,出10*/};
    /**
     * 双码流，5.7K-30FPS，虚拟
     */
    int[] CAMERA_PREVIEW_960_960_30_v = new int[]{960, 960, 30};
    /**
     * 双码流，4K-60FPS，虚拟
     */
    int[] CAMERA_PREVIEW_940_940_60_v = new int[]{940, 940, 60};
    int[] CAMERA_PREVIEW_960_960_16_v = new int[]{960, 960, 16};
    int[] CAMERA_PREVIEW_960_960_15_v = new int[]{960, 960, 15};
    int[] CAMERA_PREVIEW_960_960_14_v = new int[]{960, 960, 14};
    /**
     * 双码流，2.5K-120FPS，虚拟
     */
    int[] CAMERA_PREVIEW_920_920_120_v = new int[]{920, 920, 120};
    int[] CAMERA_PREVIEW_1220_1220_120_v = new int[]{1220, 1220, 120};
}
