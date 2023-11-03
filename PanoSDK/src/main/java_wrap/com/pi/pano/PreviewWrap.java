package com.pi.pano;

import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiAntiMode;

/**
 * 预览操作包装
 */
public class PreviewWrap {
    private static final String TAG = PreviewWrap.class.getSimpleName();

    /**
     * 初始化
     */
    public static PilotSDK initPanoView(ViewGroup parent, PanoSDKListener listener) {
        return initPanoView(parent, 1, false, listener);
    }

    public static PilotSDK initPanoView(ViewGroup parent, int cameraCount, PanoSDKListener listener) {
        return initPanoView(parent, cameraCount, false, listener);
    }

    /**
     * 初始化
     *
     * @param lensProtected 是否使用保护镜
     */
    public static PilotSDK initPanoView(ViewGroup parent, int cameraCount, boolean lensProtected, PanoSDKListener listener) {
        return new PilotSDK(parent, cameraCount, lensProtected, listener);
    }

    /**
     * 是否开启/禁用触摸事件
     *
     * @param able 是否开启/禁用触摸事件
     */
    public static void setPreviewEnableTouch(boolean able) {
        PilotSDK.setEnableTouchEvent(able);
    }

    /**
     * 开启\关闭 防抖
     */
    public static void setSteadyAble(boolean able) {
        PilotSDK.useGyroscope(able);
    }

    /**
     * 设置防抖方向
     */
    public static void setSteadyFollow(boolean follow) {
        PilotSDK.stabSetLockAxis(!follow, true, true, 0.05f);
    }

    /**
     * 平面视频 防抖跟随
     *
     * @param mask 3位二进制从高到第分别为： yaw,  pitch,  roll 三轴，对轴标识位为1：开启跟随，否则为固定
     */
    public static void setSteadyFollowWithPlane(int mask) {
        PilotSDK.stabSetLockAxis((mask & 0b100) > 0, (mask & 0b010) > 0, (mask & 0b001) > 0, 0.12f);
    }

    /**
     * 设置抗频闪
     */
    public static void setAntiMode(@PiAntiMode String mode) {
        PilotSDK.setAntiMode(mode);
    }


    /**
     * 分辨率切换
     *
     * @param params   分辨率参数
     * @param listener 监听
     * @return 分辨率是否将改变
     */
    public static boolean changeResolution(@NonNull ResolutionParams params, @NonNull DefaultChangeResolutionListener listener) {
        Log.d(TAG, "change resolution:" + params);
        int[] cameraParams = params.resolutions;
        if (cameraParams.length == 3) {
            cameraParams = new int[4];
            System.arraycopy(params.resolutions, 0, cameraParams, 0, 3);
            // 默认摄像头使用全景
            cameraParams[3] = 2;
        }
        return changeResolutionInner(cameraParams, params.forceChange, listener);
    }

    private static boolean changeResolutionInner(int[] cameraParams, boolean force, @NonNull DefaultChangeResolutionListener listener) {
        listener.fillParams(String.valueOf(cameraParams[3]), cameraParams[0], cameraParams[1], cameraParams[2]);
        listener.changeSurfaceViewSize(() -> {
            PilotSDK.changeCameraResolution(force, listener);
        });
        return true;
    }

    /**
     * 开启 AI跟踪
     */
    public static void startPreViewAiDetection(OnAIDetectionListener listener) {
        PilotSDK.detectionStart(listener);
    }

    /**
     * 重置AI（跟踪对象）
     */
    public static void resetPreViewAiDetectionTarget() {
        PilotSDK.resetDetectionTarget();
    }

    /**
     * 关闭 AI跟踪
     */
    public static void stopPreViewAiDetection() {
        PilotSDK.detectionStop();
    }
}
