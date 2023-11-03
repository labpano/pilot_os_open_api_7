package com.pi.pano;

import android.util.Log;

import com.pi.pano.error.PiError;

public class SimplePiCallback implements PiCallback {
    protected static final String TAG = "PiCallback";

    @Override
    public void onSuccess() {
        Log.d(TAG, "onSuccess()");
    }

    @Override
    public void onError(PiError error) {
        Log.e(TAG, "onError :" + error);
    }
}
