package com.pi.pano;

import com.pi.pano.annotation.PiPreviewMode;

/**
 * 手势输入操作
 */
public class Input {
    /**
     * 输入事件
     *
     * @param action       输入事件类型
     * @param pointerCount 有多少点输入,用于多点触摸
     * @param timestampNs  该事件发生时间的时间戳
     * @param pointPoses   触摸点位置数组,每个pointerCount都有xy两个值,共pointerCount*2个值
     */
    static native void onTouchEvent(int action, int pointerCount, long timestampNs, float[] pointPoses);
    static native void setPreviewMode(int mode, float rotateDegree, boolean playAnimation, float fov, float cameraDistance);
    static native int getPreviewMode();
    static native void reset();

    /**
     * 重置，保留预览模式并设置新值。
     * @param rotateDegree x轴旋转角度，应在（-36，360）之间，否则忽略。重置将同时重置y轴为0。
     */
    static native void reset2(float rotateDegree, float fov, float cameraDistance);

    /**
     * 预览参数
     */
    private static Params _lastParams;

    static void keepRotateDegreeOnReset(boolean keep) {
        Params params = _lastParams;
        if (params != null) {
            params.keepRotateDegreeOnReset = keep;
        }
    }

    static void onTouchEvent2(int action, int pointerCount, long timestampNs, float[] pointPoses) {
        onTouchEvent(action, pointerCount, timestampNs, pointPoses);
    }

    private final static class Params {
        int mode;
        float rotateDegree;
        float fov;
        float cameraDistance;

        /**
         * 重置时保留旋转角度
         */
        boolean keepRotateDegreeOnReset = false;
    }

    static void setPreviewMode2(int mode, float rotateDegree, boolean playAnimation, float fov, float cameraDistance) {
        Params params = _lastParams;
        if (params == null) {
            params = new Params();
            _lastParams = params;
        }
        params.mode = mode;
        params.rotateDegree = rotateDegree;
        params.fov = fov;
        params.cameraDistance = cameraDistance;
        setPreviewMode(mode, rotateDegree, playAnimation, fov, cameraDistance);
    }

    static void reset2() {
        Params params = _lastParams;
        if (null != params) {
            if (params.mode == PiPreviewMode.vlog) {
                return;
            }
            reset2(params.keepRotateDegreeOnReset ? 360 : params.rotateDegree, params.fov, params.cameraDistance);
            return;
        }
        reset();
    }
}