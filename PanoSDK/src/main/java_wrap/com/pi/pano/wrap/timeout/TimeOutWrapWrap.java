package com.pi.pano.wrap.timeout;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 超时辅助类
 */
public class TimeOutWrapWrap implements ITimeOutWrap {
    private static String TAG = "";
    private static final int MESSAGE_TIME_OUT = 0X1;
    private static final AtomicInteger COUNT = new AtomicInteger(0);
    private final Handler mHandler;
    private long mTimeoutMs;
    private ITimeOutCallback mCallback;
    private AtomicBoolean mStop = new AtomicBoolean(false);

    public TimeOutWrapWrap(ITimeOutCallback callback) {
        mCallback = callback;
        String name = "TimeOutWrapWrap-" + COUNT.incrementAndGet();
        TAG = name;
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        mHandler = new Handler(thread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                if (msg.what == MESSAGE_TIME_OUT) {
                    Log.d(TAG, "onTimeOut :" + mCallback);
                    if (mCallback != null) {
                        mCallback.onTimeout(TimeOutWrapWrap.this);
                    }
                }
            }
        };
    }

    @Override
    public void start(long timeOutMs) {
        if (timeOutMs <= 0) {
            return;
        }
        if (mStop.get()) {
            return;
        }
        mTimeoutMs = timeOutMs;
        mHandler.removeMessages(MESSAGE_TIME_OUT);
        mHandler.sendEmptyMessageDelayed(MESSAGE_TIME_OUT, timeOutMs);
    }

    @Override
    public void cancel() {
        if (mStop.get()) {
            return;
        }
        mHandler.removeMessages(MESSAGE_TIME_OUT);
    }

    public void restart() {
        if (mStop.get()) {
            return;
        }
        if (mTimeoutMs > 0) {
            mHandler.sendEmptyMessageDelayed(MESSAGE_TIME_OUT, mTimeoutMs);
        }
    }

    @Override
    public void stop() {
        if (mStop.get()) {
            return;
        }
        mStop.set(true);
        mCallback = null;
        mHandler.removeMessages(MESSAGE_TIME_OUT);
        mHandler.getLooper().quit();
    }

    public interface ITimeOutCallback {
        void onTimeout(ITimeOutWrap timeOutWrap);
    }

}
