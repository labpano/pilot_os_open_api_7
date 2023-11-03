package com.pi.pano;

import android.util.Log;
import android.view.Surface;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiAntiMode;
import com.pi.pano.annotation.PiEncodeSurfaceType;
import com.pi.pano.wrap.ProParams;
import com.pi.pano.wrap.annotation.PiPreviewType;

public class PreviewManagerImpl implements IPreviewManager {
    private static final String TAG = "PreviewManager";

    @Override
    public void initPreviewView(ViewGroup parent, PanoSDKListener listener) {
        initPreviewView(parent, PiPreviewType.IMAGE, listener);
    }

    @Override
    public void initPreviewView(ViewGroup parent, @PiPreviewType int previewType, PanoSDKListener listener) {
        initPreviewView(parent, previewType, false, listener);
    }

    @Override
    public void initPreviewView(ViewGroup parent, @PiPreviewType int previewType, boolean lensProtected, PanoSDKListener listener) {
        int cameraCount = 1;
        if (PiPreviewType.VIDEO_PANO == previewType) {
            cameraCount = 2;
        }
        PilotSDK sdk = new PilotSDK(parent, cameraCount, lensProtected, listener);
        CameraSurfaceView mCameraSurfaceView = sdk.mCameraSurfaceView;
        Log.d(TAG, "initPreviewView :" + mCameraSurfaceView);
    }

    @Override
    public void changeResolution(@NonNull ResolutionParams params, @NonNull DefaultChangeResolutionListener listener) {
        Log.d(TAG, "changeResolution :" + params);
        int[] cameraParams = params.resolutions;
        if (cameraParams.length == 3) {
            cameraParams = new int[4];
            System.arraycopy(params.resolutions, 0, cameraParams, 0, 3);
            // 默认摄像头使用全景
            cameraParams[3] = 2;
        }
        listener.fillParams(String.valueOf(cameraParams[3]), cameraParams[0], cameraParams[1], cameraParams[2]);
        listener.changeSurfaceViewSize(() -> PilotSDK.changeCameraResolution(params.forceChange, listener));
    }

    @Override
    public void restorePreview(@Nullable PiCallback callback) {
        PilotSDK.restorePreview(callback);
    }

    @Override
    public void startPreviewWithTakeLargePhoto(@NonNull PiCallback callback) {
        PilotSDK.startPreviewWithTakeLargePhoto(callback);
    }

    @Override
    public void setPreviewEnable(boolean enable) {
        PilotSDK.setPanoEnable(enable);
    }

    @Override
    public void setProParams(@NonNull ProParams params, @Nullable PiCallback callback) {
        PilotSDK.setProParams(params, callback);
    }

    @Override
    public void setInPhotoHdr(boolean hdrAble) {
        PilotSDK.setInPhotoHdr(hdrAble);
    }

    @Override
    public void setSteadyAble(boolean open) {
        PilotSDK.useGyroscope(open);
    }

    @Override
    public void setSteadyFollow(boolean isFollow, boolean isPano) {
        if (isPano) {
            PilotSDK.stabSetLockAxis(!isFollow, true, true, 0.05f);
        } else {
            PilotSDK.stabSetLockAxis(false, false, isFollow, 0.12f);
        }
    }

    @Override
    public void setAntiMode(@PiAntiMode String mode) {
        PilotSDK.setAntiMode(mode);
    }

    @Override
    public boolean isCameraPreviewing() {
        return PilotSDK.isCameraPreviewing();
    }

    @Override
    public void setEncodeInputSurface(Surface surface, @PiEncodeSurfaceType int surfaceType) {
        PilotSDK.setEncodeSurface(surface, surfaceType);
    }

    @Override
    public int getFps() {
        return PilotSDK.getFps();
    }

    /**
     * 设置是否使用 保护镜
     *
     * @param enable true:使用
     */
    public void setLensProtected(boolean enable) {
        PilotSDK.setLensProtected(enable);
    }


}
