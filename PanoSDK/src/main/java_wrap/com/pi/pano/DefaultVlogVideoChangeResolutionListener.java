package com.pi.pano;

import com.pi.pano.annotation.PiLensCorrectionMode;
import com.pi.pano.annotation.PiPreviewMode;

public class DefaultVlogVideoChangeResolutionListener extends DefaultChangeResolutionListener {

    public DefaultVlogVideoChangeResolutionListener() {
        super();
    }

    @Override
    protected void setPreviewParam() {
        PilotSDK.setParamReCaliEnable(PilotSDK.getFps() * 2, false);
        PilotSDK.setLensCorrectionMode(PiLensCorrectionMode.PANO_MODE_2);
        PilotSDK.setPreviewMode(PiPreviewMode.planet, 180, false, fieldOfView, 0);
    }

    @Override
    protected void initStabilizationTimeOffset() {
        PilotSDK.setStabilizationMediaHeightInfo(CameraPreview.CAMERA_PREVIEW_2900_2900_30[1]);
    }


    @Override
    protected boolean isLockDefaultPreviewFps() {
        return true;
    }
}
