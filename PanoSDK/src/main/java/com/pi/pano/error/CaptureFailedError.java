package com.pi.pano.error;

import android.util.Log;

public class CaptureFailedError {
    private static final String TAG = CaptureFailedError.class.getSimpleName();

    /**
     * 最大检测时间s,超过则不检查
     */
    private static final long MAX_CHECK_TIME = 10;
    /**
     * 捕获失败的,最大间隔时间s
     */
    private static final long MAX_INTERVAL_TIME = 8;
    /**
     * 多次捕获失败,最大计算时间s
     */
    private static final long MAX_MANY_FAILED_TIME = 3;
    private static final int MAX_FAILED_COUNT = 30;
    /**
     * 捕获失败的,最大间隔次数
     */
    private static final int MAX_INTERVAL_FAILED_COUNT = 2;
    private long lastCaptureTime;
    private int failedCount;
    private long lastDiffTime;
    private long lastDiffCount;
    private String mErrorInfo;

    public int getFailedCount() {
        return failedCount;
    }

    public boolean checkFailed() {
        long curTime = System.currentTimeMillis();
        long diff = Math.round((curTime - lastCaptureTime) / 1000f);
        Log.w(TAG, "checkFailed " + diff + ",last:" + lastCaptureTime
                + ",lastDiffTime: " + lastDiffTime + ",lastDiffCount:" + lastDiffCount);
        if (lastCaptureTime == 0) {
            lastCaptureTime = curTime;
        } else if (diff > MAX_CHECK_TIME) {
            reset();
        } else if (diff >= MAX_INTERVAL_TIME) {
            if (lastDiffTime == 0) {
                resetDiff(diff);
            } else if (diff == lastDiffTime) {
                lastDiffCount++;
                if (lastDiffCount >= MAX_INTERVAL_FAILED_COUNT) {
                    //处理已发现的 规律性间隔报错
                    mErrorInfo = "onCaptureFailed by interval";
                    return false;
                }
            }
            lastCaptureTime = curTime;
        } else if (diff <= MAX_MANY_FAILED_TIME
                && failedCount >= MAX_FAILED_COUNT) {
            //短时间,一直报错
            mErrorInfo = "onCaptureFailed many failed";
            return false;
        }
        failedCount++;
        return true;
    }

    public String getErrorInfo() {
        return mErrorInfo;
    }

    private void resetDiff(long diffTime) {
        lastDiffTime = diffTime;
        lastDiffCount = 1;
    }

    public void reset() {
        failedCount = 0;
        lastCaptureTime = 0;
        lastDiffTime = 0;
        lastDiffCount = 0;
        mErrorInfo = null;
    }

}
