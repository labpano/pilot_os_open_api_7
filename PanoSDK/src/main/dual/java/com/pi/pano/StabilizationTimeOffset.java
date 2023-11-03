package com.pi.pano;

/**
 * 相机图像和陀螺仪之间的时间戳偏移
 */
public @interface StabilizationTimeOffset {
    /**
     * 5.7K拍照
     */
    long[] PHOTO_5_7K = new long[]{54000000, 31467014};
    /**
     * 8k全景视频
     */
    long[] NORMAL_VIDEO_8K = new long[]{45000000, 63000000};
    long[] NORMAL_VIDEO_5_7K = new long[]{18500000, 31467014};
    long[] NORMAL_VIDEO_4K = new long[]{54000000, 31467014};
    /**
     * 8K延时摄影
     */
    long[] TIME_LAPSE_VIDEO_8K = new long[]{45000000, 63000000};
    long[] TIME_LAPSE_VIDEO_5_7K = new long[]{54000000, 31467014};
    /**
     * 8K街景视频
     */
    long[] STREET_VIEW_VIDEO_8K = new long[]{45000000, 63000000};
    long[] STREET_VIEW_VIDEO_5_7K = new long[]{54000000, 31467014};

    long[] LIVE_5_7K = new long[]{54000000, 31467014};
}
