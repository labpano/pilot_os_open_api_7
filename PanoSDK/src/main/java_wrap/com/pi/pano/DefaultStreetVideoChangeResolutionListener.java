package com.pi.pano;

import com.pi.pano.annotation.PiLensCorrectionMode;
import com.pi.pano.annotation.PiPreviewMode;

public class DefaultStreetVideoChangeResolutionListener extends DefaultChangeResolutionListener {

    public DefaultStreetVideoChangeResolutionListener() {
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
        int[] data = mWidth == CameraPreview.CAMERA_PREVIEW_3868_3868_10[0] ?
                CameraPreview.CAMERA_PREVIEW_3868_3868_10 : CameraPreview.CAMERA_PREVIEW_2900_2900_30;
        PilotSDK.setStabilizationMediaHeightInfo(data[1]);
    }


    @Override
    protected boolean isLockDefaultPreviewFps() {
        return false;
    }
}
