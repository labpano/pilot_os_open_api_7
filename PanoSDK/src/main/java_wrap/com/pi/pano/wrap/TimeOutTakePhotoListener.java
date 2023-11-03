package com.pi.pano.wrap;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.pi.pano.TakePhotoListener2;
import com.pi.pano.annotation.PiHdrCount;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class TimeOutTakePhotoListener extends TakePhotoListener2 {
    private static final String TAG = TimeOutTakePhotoListener.class.getSimpleName();
    public static final int TIMEOUT_TIME = 40_000;
    private final IPhotoListener mListener;

    protected final Handler mHandler;
    private volatile boolean isTerminate = false;
    private final AtomicInteger photoIndex = new AtomicInteger(0);

    public TimeOutTakePhotoListener(IPhotoListener listener) {
        this(listener, new Handler(Looper.getMainLooper()));
    }

    public TimeOutTakePhotoListener(IPhotoListener callback, Handler handler) {
        this.mHandler = handler;
        this.mListener = callback;
    }

    private boolean isHdr() {
        return mParams.hdrCount != PiHdrCount.out;
    }

    private final Runnable mTimeoutRunnable = () -> {
        if (isTerminate) {
            return;
        }
        terminate();
        notifyTakeError(isHdr(), PhotoErrorCode.error_timeout);
    };

    protected void terminate() {
        Log.d(TAG, "terminate =====>" + isTerminate);
        mHandler.removeCallbacks(mTimeoutRunnable);
        isTerminate = true;
    }

    @Override
    protected void onTakePhotoStart(int index) {
        super.onTakePhotoStart(index);
        if (isTerminate) {
            return;
        }
        photoIndex.incrementAndGet();
        notifyTakeStart(index);
        //
        if (photoIndex.get() == 1) {
            mHandler.postDelayed(mTimeoutRunnable, TIMEOUT_TIME);
        }
    }

    @Override
    protected void onCapturePhotoEnd() {
        super.onCapturePhotoEnd();
        if (isTerminate) {
            return;
        }
        notifyTakeEnd();
    }

    @Override
    protected void onTakePhotoComplete(int errorCode) {
        super.onTakePhotoComplete(errorCode);
        if (isTerminate) {
            return;
        }
        terminate();
        if (null != mListener) {
            if (errorCode == 0) {
                notifyTakeSuccess(isHdr(), null, mUnStitchFile);
            } else {
                notifyTakeError(isHdr(), PhotoErrorCode.error + " (" + errorCode + ")");
            }
        }
    }

    public void notifyTakeStart() {
        if (null != mListener) {
            mHandler.post(mListener::onTakeStart);
        }
    }

    protected void notifyTakeStart(int index) {
        if (null != mListener) {
            mHandler.post(() -> mListener.onTakeStart(index));
        }
    }

    protected void notifyTakeEnd() {
        if (null != mListener) {
            mHandler.post(() -> mListener.onTakeEnd());
        }
    }

    protected void notifyTakeError(boolean change, String msg) {
        if (null != mListener) {
            mHandler.post(() -> mListener.onTakeError(change, msg));
        }
    }

    protected void notifyTakeSuccess(boolean change, @Nullable File stitchFile, @Nullable File unStitchFile) {
        if (null != mListener) {
            mHandler.post(() -> mListener.onTakeSuccess(change, mParams.obtainBasicName(), stitchFile, unStitchFile));
        }
    }
}
