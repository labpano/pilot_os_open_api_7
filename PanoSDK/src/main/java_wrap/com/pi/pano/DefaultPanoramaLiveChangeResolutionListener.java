package com.pi.pano;

public class DefaultPanoramaLiveChangeResolutionListener extends DefaultChangeResolutionListener {

    public DefaultPanoramaLiveChangeResolutionListener() {
        super();
        PilotSDK.setPreviewImageReaderFormat(getPreviewImageReaderFormat());
    }

    @Override
    protected void setPreviewParam() {
        super.setPreviewParam();
        PilotSDK.reloadWatermark(true);
    }

    @Override
    protected boolean isLockDefaultPreviewFps() {
        return false;
    }
}