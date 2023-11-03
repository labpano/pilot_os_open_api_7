package com.pi.pano;

import com.pi.pano.error.PiError;

/**
 * Pi通用回调 {@link SimplePiCallback}
 */
public interface PiCallback {
    void onSuccess();

    void onError(PiError error);
}
