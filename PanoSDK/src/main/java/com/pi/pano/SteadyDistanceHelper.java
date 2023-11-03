package com.pi.pano;

/**
 * 防抖距离处理
 */
final class SteadyDistanceHelper {
    private static final int STEADY_ADD_VALUE = 1000_000;
    private static final long STEADY_MIN_VALUE = 5000_000;
    private static final long STEADY_MAX_VALUE = 25000_000;

    /**
     * 设置防抖距离,
     * 仅区分单鱼眼4k以上（不含）用14
     *
     * @param isHighResolution 是否大于4k
     */
    static void setSteadyDistance(PiPano pano, boolean isHighResolution) {
        long value;
        if (isHighResolution) {
            value = 14 * STEADY_ADD_VALUE;
        } else {
            value = 11 * STEADY_ADD_VALUE;
        }
        pano.nativeStabSetTimeoffset(-1, value);
    }
}
