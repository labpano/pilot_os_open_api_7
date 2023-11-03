package com.pi.pano;

import androidx.annotation.NonNull;

public class DefaultTimeVideoChangeResolutionListener extends DefaultChangeResolutionListener {
    public DefaultTimeVideoChangeResolutionListener() {
    }

    public DefaultTimeVideoChangeResolutionListener(@NonNull String aspectRatio) {
        super(aspectRatio);
    }

    public DefaultTimeVideoChangeResolutionListener(int fieldOfView, @NonNull String aspectRatio) {
        super(fieldOfView, aspectRatio);
    }

    @Override
    protected void initStabilizationTimeOffset() {
        int[] data = mWidth == CameraPreview.CAMERA_PREVIEW_3868_3868_10[0] ?
                CameraPreview.CAMERA_PREVIEW_3868_3868_10 : CameraPreview.CAMERA_PREVIEW_2900_2900_30;
        PilotSDK.setStabilizationMediaHeightInfo(data[1]);
    }

}
