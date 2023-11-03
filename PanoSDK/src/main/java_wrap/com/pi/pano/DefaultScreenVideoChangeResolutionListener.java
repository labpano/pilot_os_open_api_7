package com.pi.pano;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiLensCorrectionMode;
import com.pi.pano.annotation.PiPreviewMode;

public class DefaultScreenVideoChangeResolutionListener extends DefaultChangeResolutionListener {

    public DefaultScreenVideoChangeResolutionListener(@NonNull String aspectRatio) {
        super(aspectRatio);
        PilotSDK.setPreviewImageReaderFormat(getPreviewImageReaderFormat());
    }

    @Override
    protected void setPreviewParam() {
        PilotSDK.setParamReCaliEnable(PilotSDK.getFps() * 2, false);
        PilotSDK.setLensCorrectionMode(PiLensCorrectionMode.PANO_MODE_2);
        Pair<Float, Float> obtain = FieldOfViewUtils.obtain(aspectRatio, fieldOfView);
        PilotSDK.setPreviewMode(PiPreviewMode.planet, 180, false, obtain.first, obtain.second);
        PilotSDK.reloadWatermark(false);
    }

    @Override
    protected boolean isLockDefaultPreviewFps() {
        return false;
    }
}