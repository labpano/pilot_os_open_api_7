package com.pi.pano;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiLensCorrectionMode;
import com.pi.pano.annotation.PiPreviewMode;
import com.pi.pano.wrap.annotation.PiFps;

public class DefaultPlaneVideoChangeResolutionListener extends DefaultChangeResolutionListener {

    public DefaultPlaneVideoChangeResolutionListener(int fieldOfView, @NonNull String aspectRatio) {
        super(fieldOfView, aspectRatio);
    }

    @Override
    protected void setPreviewParam() {
        PilotSDK.setParamReCaliEnable(0, false);
        int rotateDegree;
        int lensCorrectionMode;
        if ("0".equals(mCameraId)) {
            rotateDegree = 180;
            lensCorrectionMode = PiLensCorrectionMode.PLANE_MODE_0;
        } else {
            lensCorrectionMode = PiLensCorrectionMode.PLANE_MODE_1;
            rotateDegree = 0;
        }
        Pair<Float, Float> obtain = FieldOfViewUtils.obtain(aspectRatio, fieldOfView);
        PilotSDK.setLensCorrectionMode(lensCorrectionMode);
        PilotSDK.setPreviewMode(PiPreviewMode.vlog, rotateDegree, false, obtain.first, obtain.second);
        initStabilizationTimeOffset();
    }

    @Override
    protected void initStabilizationTimeOffset() {
        int[] data = PiFps._60.equals("" + mFps) ? CameraPreview.CAMERA_PREVIEW_1932_1932_60 :
                CameraPreview.CAMERA_PREVIEW_2900_2900_30;
        PilotSDK.setStabilizationMediaHeightInfo(data[1]);
    }

    @Override
    protected boolean isLockDefaultPreviewFps() {
        return false;
    }
}
