package com.pi.pano;

import android.util.Log;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiExposureCompensation;
import com.pi.pilot.pano.sdk.BuildConfig;

import java.util.Arrays;

/**
 * hdr曝光时间计算
 */
class HdrExposureTimeCalculator {
    private static final String TAG = HdrExposureTimeCalculator.class.getSimpleName();

    /**
     * 获取曝光时间
     *
     * @param hdrCount         hdr张数
     * @param baseExposureTime 曝光基础值，参考此值做多个曝光
     * @return 曝光时间数组
     */
    @NonNull
    static long[] getExposureTimes(int hdrCount, long baseExposureTime) {
        long[] dst_exposure_times = new long[hdrCount];
        float[] exposureScale = getExposureTimeScales(hdrCount);
        float compensateExposureScale = getHdrExCompensateScale();
        for (int i = 0; i < dst_exposure_times.length; i++) {
            dst_exposure_times[i] = (long) (baseExposureTime * exposureScale[i] * compensateExposureScale);
        }
        Log.d(TAG, "get exposure times, base:" + baseExposureTime + ", compensate scale:" + compensateExposureScale + ", dst:" + Arrays.toString(dst_exposure_times));
        return dst_exposure_times;
    }

    /**
     * 获取曝光时间缩放比例
     *
     * @param hdrCount hdr张数
     * @return 曝光时间缩放比例数组
     */
    private static float[] getExposureTimeScales(int hdrCount) {
        if (hdrCount == 3) {
            return new float[]{1 / 4f, 1, 4f};
        } else if (hdrCount == 5) {
            return new float[]{1 / 3f, 1 / 1.5f, 1, 1.5f, 3};
        } else if (hdrCount == 7) {
            return new float[]{1 / 3f, 1 / 2f, 1 / 1.5f, 1, 1.5f, 2, 3f};
        } else if (hdrCount == 9) {
            return new float[]{1 / 3f, 1 / 2.5f, 1 / 2f, 1 / 1.5f, 1, 1.5f, 2, 2.5f, 3};
        }
        throw new RuntimeException("HdrCount isn't support!");
    }

    public static int[] getEvList(int hdrCount) {
        if (hdrCount == 3) {
            return new int[]{PiExposureCompensation.reduce_4, PiExposureCompensation.normal, PiExposureCompensation.enhance_4};
        } else if (hdrCount == 5) {
            return new int[]{PiExposureCompensation.reduce_4, PiExposureCompensation.reduce_2,
                    PiExposureCompensation.normal,
                    PiExposureCompensation.enhance_2, PiExposureCompensation.enhance_4};
        } else if (hdrCount == 7) {
            return new int[]{PiExposureCompensation.reduce_4, PiExposureCompensation.reduce_2,
                    PiExposureCompensation.reduce_1, PiExposureCompensation.normal,
                    PiExposureCompensation.enhance_1, PiExposureCompensation.enhance_2, PiExposureCompensation.enhance_4};
        } else if (hdrCount == 9) {
            return new int[]{PiExposureCompensation.reduce_4, PiExposureCompensation.reduce_3,
                    PiExposureCompensation.reduce_2, PiExposureCompensation.reduce_1,
                    PiExposureCompensation.normal,
                    PiExposureCompensation.enhance_1, PiExposureCompensation.enhance_2,
                    PiExposureCompensation.enhance_3, PiExposureCompensation.enhance_4};
        }
        throw new RuntimeException("HdrCount isn't support!");
    }

    /**
     * 关于手段曝光：
     * tuning部分压制的ISO补偿是1.9
     */
    private static float getHdrExCompensateScale() {
        if (BuildConfig.DEBUG) {
            Object photoHdrEvScale = SystemPropertiesProxy.parseDebugPropData("photoHdrEvScale");
            if (photoHdrEvScale != null) {
                try {
                    return Float.parseFloat(photoHdrEvScale.toString());
                } catch (Throwable ignore) {

                }
            }
        }
        return 1;
    }
}
