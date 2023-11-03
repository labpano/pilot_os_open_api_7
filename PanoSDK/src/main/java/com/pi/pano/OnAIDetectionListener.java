package com.pi.pano;

import androidx.annotation.Keep;

/**
 * AI监听
 */
public interface OnAIDetectionListener {
    int MSG_DETECTION = 0;
    int MSG_TRACKING = 20;

    /**
     * 检测结果状态回调
     *
     * @param msg   类型，为：检测{@link OnAIDetectionListener#MSG_DETECTION}、追踪{@link OnAIDetectionListener#MSG_TRACKING}
     * @param count msg为检测时，返回的是检测的数量；
     *              msg为追踪时，返回的时追踪连续丢失的次数；
     */
    @Keep
    void onDetectResult(int msg, int count);
}
