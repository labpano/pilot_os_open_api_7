package com.pi.pano;

import android.graphics.ImageFormat;

public class DefaultPhotoChangeResolutionListener extends DefaultChangeResolutionListener {
    protected boolean mSupportRawPhoto;

    public DefaultPhotoChangeResolutionListener() {
        this(false);
    }

    public DefaultPhotoChangeResolutionListener(boolean supportRawPhoto) {
        super();
        mSupportRawPhoto = supportRawPhoto;
        PilotSDK.setPreviewImageReaderFormat(getPreviewImageReaderFormat());
    }

    @Override
    protected int[] getPreviewImageReaderFormat() {
        return mSupportRawPhoto ? new int[]{ImageFormat.JPEG, ImageFormat.RAW_SENSOR} :
                new int[]{ImageFormat.JPEG};
    }

    @Override
    protected void initStabilizationTimeOffset() {
        PilotSDK.setStabilizationMediaHeightInfo(CameraPreview.CAMERA_PREVIEW_3868_1934_30[1]);
    }
    
}
