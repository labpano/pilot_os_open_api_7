package com.pi.pano;

import android.os.SystemClock;

import com.pi.pano.error.PiError;

public class HdrSkipFrameCallbackWrap implements PiCallback {
    private PiCallback mPiCallback;
    private PiPano mPiPano;

    public HdrSkipFrameCallbackWrap(PiCallback piCallback, PiPano pano) {
        mPiCallback = piCallback;
        mPiPano = pano;
    }

    @Override
    public void onSuccess() {
        new Thread(() -> {
            while (mPiPano.mSkipFrameWithTakeHdr > 0) {
                SystemClock.sleep(50);
            }
            if (mPiCallback != null) {
                mPiCallback.onSuccess();
            }
            release();
        }).start();
    }

    @Override
    public void onError(PiError error) {
        if (mPiCallback != null) {
            mPiCallback.onError(error);
        }
        release();
    }

    private void release() {
        mPiPano = null;
        mPiCallback = null;
    }
}
