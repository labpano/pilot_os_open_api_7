package com.pi.pano;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.location.LocationManager;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiAntiMode;
import com.pi.pano.annotation.PiCameraBehavior;
import com.pi.pano.annotation.PiExposureCompensation;
import com.pi.pano.annotation.PiExposureTime;
import com.pi.pano.annotation.PiIso;
import com.pi.pano.annotation.PiPhotoFileFormat;
import com.pi.pano.annotation.PiResolution;
import com.pi.pano.annotation.PiWhiteBalance;
import com.pi.pano.error.CaptureFailedError;
import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;
import com.pi.pano.wrap.ProParams;
import com.pi.pano.wrap.timeout.ITimeOutWrap;
import com.pi.pano.wrap.timeout.TimeOutWrapWrap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class CameraToTexture {
    private static final int CONTROL_AE_EXPOSURE_COMPENSATION_STEP = 6;
    private static final String REQUEST_TAG_HDR_DNG_RAW = "hdr_dng_raw";
    private static final int HDR_SKIP_FRAME_COUNT_5_7K = 2;
    private static final int HDR_SKIP_FRAME_COUNT_12K = 2;
    /**
     * 需要延时初始化imageReader的 拍照分辨率width 的最小值
     */
    private static final int MIN_DELAY_CREATE_IMAGE_READER_WIDTH = 8000;
    /**
     * 最大曝光时间
     */
    private static final long MAX_EXPOSURE_TIME = 1_000_000_000 / 15;

    /**
     * 5.7K 最小曝光时间（ns）。
     */
    private static final long MIN_EXPOSURE_TIME = 65232;
    /**
     * 11K 最小曝光时间（ns）。
     */
    private static final long MIN_EXPOSURE_TIME_11K = 183824;

    private static final String OP_KEY_OPEN_CAMERA = "openCamera";
    private static final String OP_KEY_START_PREVIEW = "startPreview";
    private static final String OP_KEY_UPDATE_PREVIEW = "updatePreview";
    private static final String OP_KEY_START_CAPTURE = "startCapture";
    private static final String OP_KEY_COMM_CAMERA = "commCamera";

    //记录callback状态 -无回调
    private static final int CALL_BACK_STATE_NO = -1;
    //记录callback状态 -有回调
    private static final int CALL_BACK_STATE_HAS = 1;

    // 高帧率
    private static final int HIGH_FPS = 120;

    private final String TAG;
    private final Context mContext;
    private CameraCaptureSession mCameraSession;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    private Handler mBackgroundHandler;
    private final int mIndex;
    private final SurfaceTexture mPreviewSurfaceTexture;
    private final PiPano mPiPano;
    private CameraCharacteristics mCameraCharacteristics;
    private CameraEnvParams mCameraEnvParams;
    private long mActualExposureTime = -1;
    private int mActualSensitivity = -1;

    private int mExposeMode = CaptureRequest.CONTROL_AE_MODE_ON;
    private final ProParams mProParams;
    @PiAntiMode
    private String mAntiMode = PiAntiMode.auto;

    /**
     * 测光模式.
     * 0: Average 1: Center 2:Spot 3: Custom
     */
    private final int metering = 1;
    @SuppressLint("NewApi")
    public static final CaptureRequest.Key<Integer> METERING_MODE = new CaptureRequest.Key<>("org.codeaurora.qcamera3.exposure_metering.exposure_metering_mode", int.class);
    @SuppressLint("NewApi")
    public static final CaptureRequest.Key<Integer> FORCE_HW_SYNC = new CaptureRequest.Key<>("com.labpano.qcamera3.requestConfig.forceHwSync", int.class);

    private ImageReader mJpegImageReader;
    private ImageReader mRawImageReader;

    /**
     * 是否锁定默认预览帧率
     */
    private boolean mLockDefaultPreviewFps = true;
    /**
     * 预览时，需要准备的图像格式
     */
    private int[] mPreviewImageReaderFormat;
    private final Map<String, CameraCallbackWrap> mCallbackMap = new ConcurrentHashMap<>();

    private final boolean force_hw_sync_enable;
    private final AtomicInteger mHdrCount = new AtomicInteger(0);
    private final AtomicBoolean mCameraPreviewSuccess = new AtomicBoolean(false);
    private HdrImageProcessNor mHdrImageProcessNor;
    private HdrImageProcess mHdrImageProcessNor2;

    /**
     * 录像第一帧时间戳
     */
    private long mFirstRecordSensorTimestamp;

    CameraToTexture(Context context, int index, SurfaceTexture surfaceTexture, PiPano piPano) {
        TAG = "CameraToTexture-" + index;
        mContext = context.getApplicationContext();
        mIndex = index;
        mPreviewSurfaceTexture = surfaceTexture;
        mPiPano = piPano;
        mProParams = new ProParams(
                PiExposureTime.auto, PiExposureCompensation.normal,
                PiIso._100, PiWhiteBalance.auto);
        force_hw_sync_enable = SystemPropertiesProxy.getBoolean(Config.PERSIST_DEV_PANO_DUAL_FORCE_HW_SYNC_ENABLE, true);
    }

    CameraEnvParams getCameraEnvParams() {
        if (null == mCameraDevice) {
            return null;
        }
        return mCameraEnvParams;
    }

    private final CameraDevice.StateCallback mStateCallback = new InnerCameraStateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            super.onOpened(cameraDevice);
            startPreview(defaultResolutionPreviewCallback(), false);
        }
    };

    public ProParams getProParams() {
        return mProParams;
    }

    /**
     * Camera 状态回调
     */
    private class InnerCameraStateCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "onOpened :" + cameraDevice);
            mCameraDevice = cameraDevice;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.w(TAG, "onDisconnected :" + cameraDevice);
            mCameraDevice = null;
            onCallbackError(PiErrorCode.CAMERA_NOT_OPENED, "cameraDevice onDisconnected ");
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Log.e(TAG, "open camera onError :" + error + "," + cameraDevice);
            mCameraDevice = null;
            onCallbackError(PiErrorCode.CAMERA_NOT_OPENED, "openCamera error :" + error);
            //此方法系统可能会阻塞
            cameraDevice.close();
        }
    }

    private boolean onCallbackSuccess(String key) {
        CameraCallbackWrap callbackWrap = mCallbackMap.remove(key);
        Log.d(TAG, "onCallbackSuccess key:" + key + " ," + callbackWrap);
        if (OP_KEY_UPDATE_PREVIEW.equals(key)) {
            mCameraPreviewSuccess.set(true);
        }
        boolean success = callbackWrap != null && callbackWrap.mPiCallback != null;
        if (success) {
            callbackWrap.mPiCallback.onSuccess();
        }
        return success;
    }

    private void onCallbackError(String key, @PiErrorCode int code, String message) {
        mCameraPreviewSuccess.set(false);
        CameraCallbackWrap callbackWrap = mCallbackMap.remove(key);
        PiError error = new PiError(code, message);
        Log.e(TAG, "onCallbackError key:" + key + "," + error + "==> result: " + callbackWrap);
        if (callbackWrap != null) {
            if (CALL_BACK_STATE_NO == callbackWrap.mState) {
                //走统一的回调
                CameraCallbackWrap wrap = mCallbackMap.get(OP_KEY_COMM_CAMERA);
                Log.e(TAG, "onCallbackError key:" + key + "," + code + "," + message + "==> comm result: " +
                        wrap);
                if (wrap != null && wrap.mPiCallback != null) {
                    wrap.mPiCallback.onError(error);
                }
                return;
            }
            if (CALL_BACK_STATE_HAS == callbackWrap.mState
                    && callbackWrap.mPiCallback != null) {
                //首次回调
                mCallbackMap.put(key, callbackWrap);
                callbackWrap.mPiCallback.onError(error);
            }
        }
    }

    private void onCallbackError(@PiErrorCode int code, String message) {
        mCameraPreviewSuccess.set(false);
        PiError error = new PiError(code, message);
        Log.e(TAG, "onCallbackError ALL " + error + "," + mCallbackMap.size());
        List<String> del = new ArrayList<>();
        mCallbackMap.forEach((s, callback) -> {
            if (callback != null) {
                if (OP_KEY_COMM_CAMERA.equals(s)) {
                    return;
                }
                if (CALL_BACK_STATE_HAS == callback.mState && callback.mPiCallback != null) {
                    Log.e(TAG, "onCallbackError ALL key:" + s + " ==> " + callback);
                    del.add(s);
                    callback.mPiCallback.onError(error);
                }
            }
        });
        if (del.isEmpty()) {
            CameraCallbackWrap wrap = mCallbackMap.get(OP_KEY_COMM_CAMERA);
            Log.e(TAG, "onCallbackError ALL comm :" + code + ": " + message + "," + wrap);
            if (wrap != null && wrap.mPiCallback != null) {
                wrap.mPiCallback.onError(error);
            }
        }
        del.forEach(mCallbackMap::remove);
        del.clear();
    }

    private abstract class InnerCaptureSessionStateCallback extends CameraCaptureSession.StateCallback {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCameraSession = session;
            Log.d(TAG, "onConfigured,session:" + session);
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "onConfigureFailed,session:" + session);
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "onClosed,session:" + session);
        }
    }

    private class InnerCaptureCallback extends CameraCaptureSession.CaptureCallback {
        private CaptureFailedError mCaptureFailedError;

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            Log.e(TAG, "onCaptureBufferLost");
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e(TAG, "onCaptureFailed :" + failure.getReason());
            if (mCaptureFailedError == null) {
                mCaptureFailedError = new CaptureFailedError();
            }
            if (!mCaptureFailedError.checkFailed()) {
                onCallbackError(OP_KEY_COMM_CAMERA, PiErrorCode.CAMERA_MANY_CAPTURE_FAILED,
                        mCaptureFailedError.getErrorInfo());
                mCaptureFailedError = null;
            }
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        Log.d(TAG, "startBackgroundThread ," + mIndex + "," + mBackgroundHandler);
        if (mBackgroundHandler == null) {
            HandlerThread handlerThread = new HandlerThread("CameraBackground-" + mIndex);
            handlerThread.start();
            mBackgroundHandler = new Handler(handlerThread.getLooper());
        }
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        Log.d(TAG, "stopBackgroundThread :" + mBackgroundHandler + ",," + Thread.currentThread().getName());
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeCallbacksAndMessages(null);
            Looper looper = mBackgroundHandler.getLooper();
            looper.quitSafely();
            try {
                Thread thread = looper.getThread();
                long timeout = SystemPropertiesProxy.getLong(Config.PERSIST_DEV_TIMEOUT_CAMERA_RELEASE, Config.MAX_RELEASE_CAMERA_TIMEOUT);
                // 线程失败也返回，避免可能引起UI等待
                thread.join(timeout);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mBackgroundHandler = null;
        }
        Log.d(TAG, "stopBackgroundThread end :" + Thread.currentThread().getName());
    }

    int checkCameraBehavior(String cameraId, int width, int height, int fps, boolean forceChange) {
        Log.i(TAG, "openCamera :" + cameraId + "," + width + "*" + height +
                ",fps: " + fps + ",forceChange: " + forceChange + "==>old:" + mCameraEnvParams);
        if (cameraId == null) {
            cameraId = "2";
        }
        String lastCameraId = mCameraEnvParams == null ? null : mCameraEnvParams.getCameraId();

        boolean initCamera = mCameraDevice == null || TextUtils.isEmpty(lastCameraId);

        String captureMode = mPreviewImageReaderFormat == null ?
                CameraEnvParams.CAPTURE_STREAM : CameraEnvParams.CAPTURE_PHOTO;
        boolean switchCapture = false;
        boolean sizeChanged = true;
        boolean fpsChanged = true;
        boolean lockFpsChanged = false;
        if (mCameraEnvParams == null) {
            mCameraEnvParams = new CameraEnvParams(cameraId, width, height, fps, captureMode, mLockDefaultPreviewFps);
        } else {
            sizeChanged = width != mCameraEnvParams.getWidth() || height != mCameraEnvParams.getHeight();
            fpsChanged = fps != mCameraEnvParams.getFps();
            mCameraEnvParams.setCameraId(cameraId);
            mCameraEnvParams.setSize(width, height);
            mCameraEnvParams.setFps(fps);
            switchCapture = !captureMode.equals(mCameraEnvParams.getCaptureMode());
            mCameraEnvParams.setCaptureMode(captureMode);
            lockFpsChanged = mCameraEnvParams.isLockDefaultPreviewFps() != mLockDefaultPreviewFps;
            mCameraEnvParams.setLockDefaultPreviewFps(mLockDefaultPreviewFps);
        }
        Log.i(TAG, "checkOrOpenCamera switchCapture:" + switchCapture + ",sizeChanged:" + sizeChanged +
                ",forceChange:" + forceChange + " ,result ==> " + mCameraEnvParams);

        boolean switchCamera = (!TextUtils.isEmpty(lastCameraId) && !TextUtils.equals(lastCameraId, cameraId))
                || fpsChanged || lockFpsChanged;
        boolean openCamera = initCamera || switchCamera;

        Log.d(TAG, "checkOrOpenCamera open: " + openCamera + ",switchCamera:" + switchCamera
                + ",initCamera:" + initCamera + ",fps:" + fpsChanged);
        return PiCameraBehavior.SWITCH;
    }

    @SuppressWarnings("MissingPermission")
    void doCameraBehavior(@PiCameraBehavior int cameraBehavior, ProParams proParams, PiCallback commCallback) {
        Log.d(TAG, "doCameraBehavior " + cameraBehavior + ",," + mProParams);
        if (PiCameraBehavior.SWITCH == cameraBehavior) {
            releaseCamera();
        }
        if (proParams != null) {
            proParams.copyTo(this.mProParams);
        }
        resetPreviewSize();
        startBackgroundThread();
        if (PiCameraBehavior.OPEN == cameraBehavior || PiCameraBehavior.SWITCH == cameraBehavior) {
            String cameraId = mCameraEnvParams.getCameraId();
            CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            try {
                mCallbackMap.put(OP_KEY_OPEN_CAMERA, new CameraCallbackWrap(commCallback, CALL_BACK_STATE_HAS));
                mCameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (OpenGLThread.sDebug) {
//                    Utils.printCameraParams(mCameraCharacteristics);
                }
                Log.d(TAG, "doCameraBehavior openCamera start " + mBackgroundHandler);
                cameraManager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
                Log.d(TAG, "doCameraBehavior openCamera end ");
            } catch (CameraAccessException | IllegalArgumentException e) {
                e.printStackTrace();
                onCallbackError(OP_KEY_OPEN_CAMERA, PiErrorCode.CAMERA_NOT_OPENED,
                        "openCamera(" + cameraId + "),error:" + e.getMessage());
            }
            return;
        }
//        todo 待处理
//        if (PiCameraBehavior.START_PREVIEW == cameraBehavior) {
//            startPreview(defaultResolutionPreviewCallback(), false);
//            return;
//        }
//        try {
//            Log.d(TAG, "checkOrOpenCamera updatePreview");
//            setCaptureRequestParam(mPreviewBuilder, true);
//            updatePreview(mChangeResolutionCallback);
//        } catch (Exception e) {
//            e.printStackTrace();
//            onChangeResolutionError(PiErrorCode.UN_KNOWN, "PiCameraBehavior(3) error:" + e.getMessage());
//        }
    }

    @NonNull
    private PiCallback defaultResolutionPreviewCallback() {
        return new PiCallback() {
            @Override
            public void onSuccess() {
                onCallbackSuccess(OP_KEY_OPEN_CAMERA);
            }

            @Override
            public void onError(PiError error) {
                onCallbackError(OP_KEY_OPEN_CAMERA, error.getCode(), error.getMessage());
            }
        };
    }

    /**
     * 重置预览大小
     */
    private void resetPreviewSize() {
        mPreviewSurfaceTexture.setDefaultBufferSize(mCameraEnvParams.mWidth, mCameraEnvParams.mHeight);
        Log.d(TAG, "resetPreviewSize:" + mCameraEnvParams.mWidth + "*" + mCameraEnvParams.mHeight);
    }

    void setCaptureRequestParam(CaptureRequest.Builder builder, boolean isPreview) {
        builder.set(METERING_MODE, metering);
        boolean sync = force_hw_sync_enable && (mPiPano != null && mPiPano.mFrameAvailableTask.length > 1);
        builder.set(FORCE_HW_SYNC, sync ? 1 : 0);
        int realFps = mCameraEnvParams.getFps();
        Log.d(TAG, "capture request force hw sync:" + sync + ",," + mExposeMode + "," + mProParams + "," + realFps);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(realFps, realFps));
        if (mPiPano != null && isPreview) {
            mPiPano.setPreviewFps(mLockDefaultPreviewFps ? 30 : realFps);
        }
        if (mExposeMode == CaptureRequest.CONTROL_AE_MODE_ON) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, calculateExposureCompensation(mProParams.exposureCompensation));
            builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, ProUtils.convertRealAntiMode(mAntiMode));
        } else {
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, mProParams.iso);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / mProParams.exposeTime);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        }
        builder.set(CaptureRequest.CONTROL_AWB_MODE, ProUtils.convertRealWhiteBalance(mProParams.whiteBalance));
        //
        builder.set(CaptureRequest.CONTROL_MODE, mProParams.control_mode);
        builder.set(CaptureRequest.CONTROL_SCENE_MODE, mProParams.control_sceneMode);
    }

    void setCaptureRequestParamForHdr(CaptureRequest.Builder builder) {
        builder.set(METERING_MODE, metering);
        builder.set(FORCE_HW_SYNC, 0);
        int realFps = mCameraEnvParams.getFps();
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(realFps, realFps));
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        builder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, ProUtils.convertRealAntiMode(mAntiMode));
        builder.set(CaptureRequest.CONTROL_AWB_MODE, ProUtils.convertRealWhiteBalance(mProParams.whiteBalance));
        builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000L / 100);
        //
        builder.set(CaptureRequest.CONTROL_MODE, mProParams.control_mode);
        builder.set(CaptureRequest.CONTROL_SCENE_MODE, mProParams.control_sceneMode);
    }

    void startPreview() {
        startPreview(null, false);
    }

    void startPreview(PiCallback callback, boolean forceInitImageReader) {
        Log.d(TAG, "start preview :" + callback + "," + mCameraDevice);
        mCallbackMap.put(OP_KEY_START_PREVIEW,
                new CameraCallbackWrap(callback, callback == null ? CALL_BACK_STATE_NO : CALL_BACK_STATE_HAS));
        if (null == mCameraDevice) {
            onCallbackError(OP_KEY_START_PREVIEW, PiErrorCode.CAMERA_NOT_OPENED,
                    "startPreview error : CameraDevice is null");
            return;
        }
        stopPreview();
        mPiPano.skipFrameWithStartPreview = false;
        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
            onCallbackError(OP_KEY_START_PREVIEW, PiErrorCode.CAMERA_NOT_OPENED,
                    "startPreview createCaptureRequest error :" + e.getMessage());
            return;
        }
        try {
            Surface previewSurface = newPreviewSurfaceAndCheck();
            mPreviewBuilder.addTarget(previewSurface);
            List<Surface> list = new ArrayList<>();
            list.add(previewSurface);
            Log.d(TAG, "startPreview,previewImageFormat:" + Arrays.toString(mPreviewImageReaderFormat)
                    + ",forceInitImageReader :" + forceInitImageReader);
            if (mPreviewImageReaderFormat != null) {
                if (mCameraEnvParams.getWidth() < MIN_DELAY_CREATE_IMAGE_READER_WIDTH || forceInitImageReader) {
                    checkImageReaderFormat(list, forceInitImageReader);
                }
            }
            setCaptureRequestParam(mPreviewBuilder, true);
            Log.d(TAG, "startPreview createCaptureSession");
            if (mCameraEnvParams.mFps >= HIGH_FPS) {
                Log.d(TAG, "createConstrainedHighSpeedCaptureSession");
                mCameraDevice.createConstrainedHighSpeedCaptureSession(list, new InnerCaptureSessionStateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        super.onConfigured(session);
                        mPiPano.skipFrameWithStartPreview = true;
                        if (!onCallbackSuccess(OP_KEY_START_PREVIEW)) {
                            updatePreview(null);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        super.onConfigureFailed(session);
                        onCallbackError(OP_KEY_START_PREVIEW, PiErrorCode.CAMERA_SESSION_CONFIGURE_FAILED,
                                "startPreview onConfigureFailed");
                    }
                }, mBackgroundHandler);
            } else {
                mCameraDevice.createCaptureSession(list, new InnerCaptureSessionStateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        super.onConfigured(session);
                        mPiPano.skipFrameWithStartPreview = true;
                        if (!onCallbackSuccess(OP_KEY_START_PREVIEW)) {
                            updatePreview(null);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        super.onConfigureFailed(session);
                        onCallbackError(OP_KEY_START_PREVIEW, PiErrorCode.CAMERA_SESSION_CONFIGURE_FAILED,
                                "startPreview onConfigureFailed");
                    }
                }, mBackgroundHandler);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
            onCallbackError(OP_KEY_START_PREVIEW, PiErrorCode.CAMERA_NOT_OPENED,
                    "startPreview createCaptureSession error: " + e.getMessage());
        }
        Log.d(TAG, "startPreview createCaptureSession end ");
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        updatePreview(null);
    }

    void updatePreview(PiCallback callback) {
        updatePreview(callback, 0);
    }

    void updatePreview(PiCallback callback, int skipFrameCount) {
        PiCallback realCallback = callback;
        if (realCallback != null && skipFrameCount > 0) {
            realCallback = new HdrSkipFrameCallbackWrap(callback, mPiPano);
        }
        mCallbackMap.put(OP_KEY_UPDATE_PREVIEW,
                new CameraCallbackWrap(realCallback, realCallback == null ? CALL_BACK_STATE_NO : CALL_BACK_STATE_HAS));
        if (mCameraDevice == null) {
            onCallbackError(OP_KEY_UPDATE_PREVIEW, PiErrorCode.CAMERA_NOT_OPENED,
                    "updatePreview failed : CAMERA_NOT_OPENED");
            return;
        }
        if (mCameraSession == null) {
            onCallbackError(OP_KEY_UPDATE_PREVIEW, PiErrorCode.CAMERA_SESSION_NOT_CREATE,
                    "updatePreview failed : SESSION_NOT_CREATE");
            return;
        }
        try {
            Log.d(TAG, "updatePreview start " + realCallback + ",," + mCameraSession);
            CameraCaptureSession.CaptureCallback captureCallback = createPreviewCaptureCallback();
            if (captureCallback instanceof PreviewCaptureCallback) {
                ((PreviewCaptureCallback) captureCallback).skipFrameCount = skipFrameCount;
            }
            if (mCameraSession instanceof CameraConstrainedHighSpeedCaptureSession) {
                List<CaptureRequest> highSpeedRequestList =
                        ((CameraConstrainedHighSpeedCaptureSession) mCameraSession).createHighSpeedRequestList(mPreviewBuilder.build());
                mCameraSession.setRepeatingBurst(highSpeedRequestList, captureCallback, mBackgroundHandler);
            } else {
                mCameraSession.setRepeatingRequest(mPreviewBuilder.build(),
                        captureCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
            int code = PiErrorCode.CAMERA_SESSION_NOT_CREATE;
            String message = "" + e.getMessage();
            if (e instanceof CameraAccessException || message.contains("CameraDevice")) {
                code = PiErrorCode.CAMERA_NOT_OPENED;
            }
            onCallbackError(OP_KEY_UPDATE_PREVIEW, code, "updatePreview failed:" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            onCallbackError(OP_KEY_UPDATE_PREVIEW, PiErrorCode.UN_KNOWN, "updatePreview error:" + e);
        }
    }

    void stopPreview() {
        Log.d(TAG, "stop preview,session:" + mCameraSession);
        if (mCameraSession != null) {
            mCameraSession.close();
            mCameraSession = null;
        }
        Log.d(TAG, "stop preview end  ");
    }

    void closeCamera() {
        stopPreview();
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        Log.d(TAG, "closeCamera end ");
    }

    public boolean isCameraPreviewing() {
        return mCameraDevice != null && mCameraSession != null &&
                mCameraPreviewSuccess.get();
    }

    @NonNull
    private CameraCaptureSession.CaptureCallback createPreviewCaptureCallback() {
        if (mPreviewImageReaderFormat != null) {
            return new PreviewCaptureCallback2();
        } else {
            return new PreviewCaptureCallback();
        }
    }

    private class PreviewCaptureCallback extends InnerCaptureCallback {
        boolean updatePreview = true;
        boolean updatePreviewResult = true;
        int skipFrameCount = 0;

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            if (mIndex == 0) {
                Long duration = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                if (duration != null) {
                    mPiPano.nativeStabSetExposureDuration(duration);
                } else {
                    Log.e(TAG, "onCaptureCompleted,result doesn't have SENSOR_EXPOSURE_TIME");
                }
            }
            if (mFirstRecordSensorTimestamp == -1 && mCameraSession == session) {
                //曝光长度为0表示这一帧被丢弃了
                Long duration = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                if (duration != 0) {
                    long deltaTimeMsNs = SystemClock.elapsedRealtimeNanos() - System.nanoTime();
                    mFirstRecordSensorTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) - deltaTimeMsNs;
                    Log.i(TAG, String.format("camera index %d, first timestamp %.3f", mIndex, mFirstRecordSensorTimestamp / 1000000.0));
                }
            }
            if (updatePreview && !mPiPano.isCaptureCompleted) {
                updatePreview = false;
                mPiPano.isCaptureCompleted = true;
            }
            if (updatePreviewResult) {
                updatePreviewResult = false;
                mPiPano.mSkipFrameWithTakeHdr = skipFrameCount;
                Log.d(TAG, "updatePreview success ");
                onCallbackSuccess(OP_KEY_UPDATE_PREVIEW);
            }
            super.onCaptureCompleted(session, request, result);
        }
    }

    private class PreviewCaptureCallback2 extends PreviewCaptureCallback {

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            mActualExposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            mActualSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY);
        }
    }

    void releaseCamera() {
        Log.d(TAG, "release camera...");
        ITimeOutWrap timeOutWrapWrap = new TimeOutWrapWrap(timeOutWrap -> {
            timeOutWrap.stop();
            onCallbackError(PiErrorCode.TIME_OUT, "release camera timeOut ");
        });
        long timeout = SystemPropertiesProxy.getLong(Config.PERSIST_DEV_TIMEOUT_CAMERA_RELEASE, Config.MAX_RELEASE_CAMERA_TIMEOUT);
        timeOutWrapWrap.start(timeout);
        stopPreview();
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        Log.d(TAG, "mCameraDevice close end");
        timeOutWrapWrap.stop();
        stopBackgroundThread();
        if (mPiPano != null) {
            mPiPano.isCaptureCompleted = false;
        }
        Log.i(TAG, "release camera end");
    }

    public void setProParams(@NonNull ProParams params, PiCallback callback, int skipFrame) {
        if (null == mCameraDevice || null == mPreviewBuilder) {
            Log.w(TAG, "setProParams filter camera not opened : " + mCameraDevice + "," + mPreviewBuilder);
            return;
        }
        int hasChanged = 0;
        if (params.exposeTime != null) {
            mProParams.exposeTime = params.exposeTime;
            if (params.exposeTime == PiExposureTime.auto) {
                mExposeMode = CaptureRequest.CONTROL_AE_MODE_ON;
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            } else {
                mExposeMode = CaptureRequest.CONTROL_AE_MODE_OFF;
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 1_000_000_000L / mProParams.exposeTime);
            }
            hasChanged++;
        }
        if (params.exposureCompensation != null) {
            mProParams.exposureCompensation = params.exposureCompensation;
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, calculateExposureCompensation(params.exposureCompensation));
            hasChanged++;
        }
        if (params.iso != null) {
            mProParams.iso = params.iso;
            mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, params.iso);
            hasChanged++;
        }
        if (params.whiteBalance != null) {
            mProParams.whiteBalance = params.whiteBalance;
            mPreviewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, ProUtils.convertRealWhiteBalance(params.whiteBalance));
            hasChanged++;
        }
        if (hasChanged > 0) {
            updatePreview(callback, skipFrame);
        }
    }

    /**
     * 设置场景模式。
     *
     * @param sceneMode -1:重置为默认（自动）
     */
    void setSceneMode(int sceneMode) {
        if (null == mCameraDevice || null == mPreviewBuilder) {
            Log.w(TAG, "setProParams filter camera not opened : " + mCameraDevice + "," + mPreviewBuilder);
            return;
        }
        if (sceneMode == -1) {
            mProParams.resetSceneMode();
        } else {
            mProParams.control_mode = CameraMetadata.CONTROL_MODE_USE_SCENE_MODE;
            mProParams.control_sceneMode = sceneMode;
        }
        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, mProParams.control_mode);
        mPreviewBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, mProParams.control_sceneMode);
        updatePreview(null, 0);
    }

    void setAntiMode(String value) {
        mAntiMode = value;
        Log.d(TAG, "setAntiMode to:" + value);
        if (null == mPreviewBuilder) {
            Log.e(TAG, "setAntiMode,preview isn't exist");
            return;
        }
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, ProUtils.convertRealAntiMode(value));
        updatePreview();
    }

    public void setLockDefaultPreviewFps(boolean value) {
        this.mLockDefaultPreviewFps = value;
    }

    public void setPreviewImageReaderFormat(int[] values) {
        this.mPreviewImageReaderFormat = values;
    }

    @NonNull
    private Surface newPreviewSurfaceAndCheck() {
        Surface surface = new Surface(mPreviewSurfaceTexture);
        if (!surface.isValid()) {
            Log.e(TAG, "PreviewSurfaceTexture isn't valid!!!");
            new Throwable().printStackTrace();
        }
        return surface;
    }

    /**
     * 捕获前的预览分辨率
     */
    @Nullable
    private int[] cache_cSize;

    /**
     * 开始持续捕获流
     *
     * @param surface 未拼接的录制或直播的surface
     * @param cSize   捕获中的预览分辨率
     */
    void startCapture(Surface surface, @NonNull PiCallback callback, @Nullable int[] cSize) {
        Log.d(TAG, "startCapture,start:" + surface + "," + mCameraDevice + "," + this);
        mCallbackMap.put(OP_KEY_START_CAPTURE, new CameraCallbackWrap(callback, CALL_BACK_STATE_HAS));
        if (null == mCameraDevice) {
            onCallbackError(OP_KEY_START_CAPTURE,
                    PiErrorCode.CAMERA_NOT_OPENED, "startCapture failed : CameraDevice is null");
            return;
        }
        stopPreview();
        //
        try {
            // 获取当前帧率，判断是否采用高帧率接口
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
            onCallbackError(OP_KEY_START_CAPTURE, PiErrorCode.CAMERA_NOT_OPENED,
                    "createCaptureRequest for record failed :" + e.getMessage());
            return;
        }
        if (cSize != null) {
            Log.d(TAG, "startCapture,change cSize:" + Arrays.toString(cSize));
            cache_cSize = new int[]{
                    mCameraEnvParams.mWidth,
                    mCameraEnvParams.mHeight,
                    mCameraEnvParams.mFps
            };
            mCameraEnvParams.mWidth = cSize[0];
            mCameraEnvParams.mHeight = cSize[1];
            mCameraEnvParams.mFps = cSize[2];
            // 修改预览分辨率
            resetPreviewSize();
        }
        try {
            List<Surface> surfaces = new ArrayList<>();
            Surface previewSurface = newPreviewSurfaceAndCheck();
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);
            surfaces.add(surface);
            mPreviewBuilder.addTarget(surface);

            mFirstRecordSensorTimestamp = -1;

            //
            setCaptureRequestParam(mPreviewBuilder, false);
            if (mCameraEnvParams.mFps >= HIGH_FPS) {
                Log.d(TAG, "createConstrainedHighSpeedCaptureSession");
                mCameraDevice.createConstrainedHighSpeedCaptureSession(surfaces, new InnerCaptureSessionStateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        super.onConfigured(session);
                        mCallbackMap.remove(OP_KEY_START_CAPTURE);
                        updatePreview(callback);
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        super.onConfigureFailed(session);
                        onCallbackError(OP_KEY_START_CAPTURE, PiErrorCode.CAMERA_SESSION_CONFIGURE_FAILED,
                                "createCaptureSession onConfigureFailed");
                    }
                }, mBackgroundHandler);
            } else {
                mCameraDevice.createCaptureSession(surfaces, new InnerCaptureSessionStateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        super.onConfigured(session);
                        mCallbackMap.remove(OP_KEY_START_CAPTURE);
                        updatePreview(callback);
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        super.onConfigureFailed(session);
                        onCallbackError(OP_KEY_START_CAPTURE, PiErrorCode.CAMERA_SESSION_CONFIGURE_FAILED,
                                "createCaptureSession onConfigureFailed");
                    }
                }, mBackgroundHandler);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
            onCallbackError(OP_KEY_START_CAPTURE, PiErrorCode.CAMERA_NOT_OPENED,
                    "createCaptureSession for record failed :" + e.getMessage());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            onCallbackError(OP_KEY_START_CAPTURE, PiErrorCode.RECORD_CAMERA_OUT_SURFACE_ILLEGAL,
                    "createCaptureSession for record failed :" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            onCallbackError(OP_KEY_START_CAPTURE, PiErrorCode.UN_KNOWN,
                    "createCaptureSession for record failed :" + e.getMessage());
        }
    }

    /**
     * 停止持续捕获流
     */
    void stopCapture(boolean sync) {
        restorePreviewSizeByCache();
        if (sync && mBackgroundHandler != null) {
            mBackgroundHandler.post(this::startPreview);
        } else {
            startPreview();
        }
    }

    private void restorePreviewSizeByCache() {
        Log.d(TAG, "restorePreviewSizeByCache :" + Arrays.toString(cache_cSize));
        if (null != cache_cSize) {
            mCameraEnvParams.mWidth = cache_cSize[0];
            mCameraEnvParams.mHeight = cache_cSize[1];
            mCameraEnvParams.mFps = cache_cSize[2];
            // 修改预览分辨率
            resetPreviewSize();
            cache_cSize = null;
        }
    }

    void stopCaptureByVideo(PiCallback callback) {
        restorePreviewSizeByCache();
        startPreview(new PiCallback() {
            @Override
            public void onSuccess() {
                Log.e(TAG, "stopCaptureByVideo startPreview onSuccess  ");
                updatePreview(callback);
            }

            @Override
            public void onError(PiError error) {
                Log.e(TAG, "stopCaptureByVideo startPreview onError:" + error);
                callback.onError(error);
            }
        }, false);
    }

    @SuppressLint("MissingPermission")
    void reopenCameraInCapture(Surface surface, PiCallback callback, int[] cSize) {
        Log.d(TAG, "reopenCameraInCapture start ");
        resetPreviewSize();
        startBackgroundThread();
        String cameraId = mCameraEnvParams.getCameraId();
        try {
            CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            mCallbackMap.put(OP_KEY_OPEN_CAMERA, new CameraCallbackWrap(callback, CALL_BACK_STATE_HAS));
            mCameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            cameraManager.openCamera(cameraId, new InnerCameraStateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    super.onOpened(cameraDevice);
                    mCallbackMap.remove(OP_KEY_OPEN_CAMERA);
                    startCapture(surface, callback, cSize);
                }
            }, mBackgroundHandler);
            Log.d(TAG, "reopenCameraInCapture openCamera end ");
        } catch (CameraAccessException | IllegalArgumentException e) {
            e.printStackTrace();
            onCallbackError(OP_KEY_OPEN_CAMERA, PiErrorCode.CAMERA_NOT_OPENED,
                    "openCamera(" + cameraId + "),error:" + e.getMessage());
        }
    }

    void setCommCameraCallback(PiCallback callback) {
        mCallbackMap.put(OP_KEY_COMM_CAMERA, new CameraCallbackWrap(callback, CALL_BACK_STATE_HAS));
    }

    void takePhoto(TakePhotoListener listener, boolean reset) {
        if (null == mCameraDevice) {
            listener.dispatchTakePhotoComplete(PiErrorCode.CAMERA_NOT_OPENED);
            return;
        }
        if (listener.mParams.isHdr()) {
//            if (reset) {
//                mHdrCount.set(0);
//            }
//            int[] evs = HdrExposureTimeCalculator.getEvList(listener.mParams.hdrCount);
//            int ev = evs[mHdrCount.get()];
//
//            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
//            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, calculateExposureCompensation(ev));
//            mPreviewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, ProUtils.convertRealWhiteBalance(mProParams.whiteBalance));
//            int skipFrame = PiResolution._12K.equals(listener.mParams.resolution) ?
//                    HDR_SKIP_FRAME_COUNT_12K : HDR_SKIP_FRAME_COUNT_5_7K;
//            updatePreview(new SimplePiCallback() {
//                @Override
//                public void onSuccess() {
//                    super.onSuccess();
//                    new Thread(() -> {
//                        while (mPiPano.mSkipFrameWithTakeHdr > 0) {
//                            SystemClock.sleep(20);
//                        }
//                        takePhotoForHdrNormal(listener);
//                    }).start();
//                }
//
//                @Override
//                public void onError(PiError error) {
//                    super.onError(error);
//                    listener.dispatchTakePhotoComplete(-1);
//                }
//            }, skipFrame);
            takePhotoForHdrNormal2(listener);
        } else {
            takePhotoNorInner(listener);
        }
    }

    /**
     * hdr拍照，连拍接口
     */
    private void takePhotoForHdrNormal2(TakePhotoListener listener) {
        if (mHdrImageProcessNor2 == null) {
            mHdrImageProcessNor2 = new HdrImageProcess(listener, mJpegImageReader,/* mBackgroundHandler,*/ mPiPano);
        }
        CameraCaptureSession cameraSession = mCameraSession;
        Log.d(TAG, "takePhotoForHdrInner exTime: " + mActualExposureTime + ", iso:" + mActualExposureTime);
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                CaptureRequest.Builder stillCapture;
                try {
                    stillCapture = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                } catch (CameraAccessException | IllegalStateException e) {
                    e.printStackTrace();
                    endCapture();
                    onTakePhotoError(listener, PiErrorCode.CAMERA_NOT_OPENED);
                    return;
                }
                try {
                    stillCapture.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                    setCaptureRequestParamForHdr(stillCapture);
                    fillExtInfo(stillCapture, listener.mParams);
                    stillCapture.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
                    stillCapture.set(CaptureRequest.SENSOR_SENSITIVITY, mActualSensitivity);

                    stillCapture.addTarget(newPreviewSurfaceAndCheck());
                    stillCapture.addTarget(mJpegImageReader.getSurface());
                    List<CaptureRequest> requests = new ArrayList<>();
                    {
                        Range<Long> exposure_time_range = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                        long min_exposure_time = Math.max(exposure_time_range.getLower(), PiResolution._12K.equals(listener.mParams.resolution) ? MIN_EXPOSURE_TIME_11K : MIN_EXPOSURE_TIME);
                        //long max_exposure_time = Math.min(exposure_time_range.getUpper(), MAX_EXPOSURE_TIME);
                        long max_exposure_time = exposure_time_range.getUpper();
                        Log.d(TAG, "takePhotoForHdr,ActualExposureTime:" + mActualExposureTime + ",ActualSensitivity:" + mActualSensitivity);
                        long[] dst_exposure_times = HdrExposureTimeCalculator.getExposureTimes(listener.mParams.hdrCount, mActualExposureTime);
                        for (int pos = 0; pos < dst_exposure_times.length; pos++) {
                            long dst_exposure_time = dst_exposure_times[pos];
                            if (dst_exposure_time < min_exposure_time) {
                                dst_exposure_time = min_exposure_time;
                            }
                            if (dst_exposure_time > max_exposure_time) {
                                dst_exposure_time = max_exposure_time;
                            }
                            stillCapture.set(CaptureRequest.SENSOR_EXPOSURE_TIME, dst_exposure_time);
                            Log.d(TAG, "takePhotoForHdr,[" + pos + "] dst_exposure_time:" + dst_exposure_time);
                            requests.add(stillCapture.build());
                        }
                    }
                    mHdrCount.set(0);
                    cameraSession.captureBurst(requests, new CameraCaptureSession.CaptureCallback() {
                        boolean captureFailed = false;

                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            Integer integer = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
                            Log.d(TAG, "onCaptureCompleted ev:" + integer);
                            if (listener.mParams.hdrCount == mHdrCount.incrementAndGet()) {
                                listener.onCapturePhotoEnd();
                            }
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                            Log.e(TAG, "onCaptureFailed :" + failure.getReason() + ",," + failure.getFrameNumber() + ",," + failure.getSequenceId());
                            captureFailed = true;
                            callTakePhotoError(listener, PiErrorCode.TAKE_PHOTO_CAPTURE_FAILED);
                            restorePreviewHdr();
                        }

                        @Override
                        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                            Log.d(TAG, "onCaptureSequenceCompleted :" + sequenceId + ",," + frameNumber);
                            handleStackHdr(captureFailed);
                        }

                        @Override
                        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                            super.onCaptureSequenceAborted(session, sequenceId);
                            Log.d(TAG, "onCaptureSequenceAborted :" + sequenceId);
                            captureFailed = true;
                            endCapture();
                            callTakePhotoError(listener, PiErrorCode.TAKE_PHOTO_CAPTURE_FAILED);
                            restorePreviewHdr();
                        }
                    }, mBackgroundHandler);
                } catch (CameraAccessException | IllegalStateException ex) {
                    ex.printStackTrace();
                    int code = PiErrorCode.CAMERA_SESSION_NOT_CREATE;
                    String message = "" + ex.getMessage();
                    if (ex instanceof CameraAccessException || message.contains("CameraDevice")) {
                        code = PiErrorCode.CAMERA_NOT_OPENED;
                    }
                    onTakePhotoError(listener, code);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    onTakePhotoError(listener, PiErrorCode.UN_KNOWN);
                }
            }

            private void endCapture() {
                if (mHdrImageProcessNor2 != null) {
                    mHdrImageProcessNor2.aborted();
                }
                mHdrImageProcessNor2 = null;
            }

            private void handleStackHdr(boolean hasCaptureFailed) {
                if (hasCaptureFailed) {
                    return;
                }
                new Thread(() -> {
                    int errCode = mHdrImageProcessNor2.stackHdrConfigured();
                    endStackHdr();
                    listener.onTakePhotoComplete(errCode);
                    endCapture();
                }).start();
                restorePreviewHdr();
            }

            private void restorePreviewHdr() {
                mBackgroundHandler.post(() -> {
                    PiCallback callback = new PiCallback() {
                        @Override
                        public void onSuccess() {
                            mPiPano.setPanoEnabled(true);
                        }

                        @Override
                        public void onError(PiError error) {
                            onCallbackError(OP_KEY_COMM_CAMERA, PiErrorCode.HDR_PHOTO_RESTORE_PREVIEW_ERROR,
                                    error.getMessage());
                            mPiPano.setPanoEnabled(true);
                        }
                    };
                    if (PiResolution._12K.equals(listener.mParams.resolution)) {
                        startPreview(new PiCallback() {
                            @Override
                            public void onSuccess() {
                                updatePreview(new PiCallback() {
                                    @Override
                                    public void onSuccess() {
                                        new Thread(() -> {
                                            while (mPiPano.mSkipFrameWithTakeHdr > 0) {
                                                SystemClock.sleep(20);
                                            }
                                            SystemClock.sleep(500);
                                            callback.onSuccess();
                                        }).start();
                                    }

                                    @Override
                                    public void onError(PiError error) {
                                        callback.onError(error);
                                    }
                                }, HDR_SKIP_FRAME_COUNT_12K);
                            }

                            @Override
                            public void onError(PiError error) {
                                callback.onError(error);
                            }
                        }, false);
                    } else {
                        //需要等待更新结果后，才恢复预览，否则画面会有亮度变化
                        setProParams(mProParams, callback, HDR_SKIP_FRAME_COUNT_5_7K);
                    }
                });
            }

            private void endStackHdr() {
                resetPreviewSize();
            }

            private void onTakePhotoError(TakePhotoListener listener, int code) {
                endCapture();
                callTakePhotoError(listener, code);
                restorePreviewHdr();
            }
        });
    }

    /**
     * hdr拍照，普通拍摄
     */
    private void takePhotoForHdrNormal(TakePhotoListener listener) {
        int[] evs = HdrExposureTimeCalculator.getEvList(listener.mParams.hdrCount);
        int ev = evs[mHdrCount.get()];
        Log.d(TAG, "takePhotoForHdrNormal start " + ev + "," + mHdrCount.get() + "," + mHdrImageProcessNor);
        if (mHdrImageProcessNor == null) {
            mHdrImageProcessNor = new HdrImageProcessNor(listener, mJpegImageReader, new SimplePiCallback() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "onSuccess :" + mHdrCount.incrementAndGet());
                    int count = mHdrCount.get();
                    if (count < listener.mParams.hdrCount) {
                        takePhoto(listener, false);
                    } else if (count == listener.mParams.hdrCount) {
                        listener.onCapturePhotoEnd();
                        mBackgroundHandler.post(() -> {
                            PiCallback callback = new PiCallback() {
                                @Override
                                public void onSuccess() {
                                    mPiPano.setPanoEnabled(true);
                                }

                                @Override
                                public void onError(PiError error) {
                                    onCallbackError(OP_KEY_COMM_CAMERA, PiErrorCode.HDR_PHOTO_RESTORE_PREVIEW_ERROR,
                                            error.getMessage());
                                    mPiPano.setPanoEnabled(true);
                                }
                            };
                            if (PiResolution._12K.equals(listener.mParams.resolution)) {
                                startPreview(new PiCallback() {
                                    @Override
                                    public void onSuccess() {
                                        updatePreview(callback);
                                    }

                                    @Override
                                    public void onError(PiError error) {
                                        callback.onError(error);
                                    }
                                }, false);
                            } else {
                                //需要等待更新结果后，才恢复预览，否则画面会有亮度变化
                                setProParams(mProParams, callback, -1);
                            }
                        });
                    } else if (mHdrCount.get() == listener.mParams.hdrCount + 1
                            && mHdrImageProcessNor != null) {
                        listener.onTakePhotoComplete(0);
                        mHdrCount.set(0);
                        mHdrImageProcessNor = null;
                    }
                }

                @Override
                public void onError(PiError error) {
                    super.onError(error);
                    if (mHdrImageProcessNor != null) {
                        mHdrImageProcessNor.aborted();
                        mHdrImageProcessNor = null;
                    }
                    callTakePhotoError(listener, error.getCode());
                }
            }, mBackgroundHandler, mPiPano);
        }
        CameraCaptureSession cameraSession = mCameraSession;
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                CaptureRequest.Builder stillCapture;
                try {
                    stillCapture = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                } catch (CameraAccessException | IllegalStateException e) {
                    e.printStackTrace();
                    if (mHdrImageProcessNor != null) {
                        mHdrImageProcessNor.aborted();
                    }
                    onTakePhotoError(listener, PiErrorCode.CAMERA_NOT_OPENED);
                    return;
                }
                try {
                    stillCapture.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                    setCaptureRequestParamForHdr(stillCapture);
                    fillExtInfo(stillCapture, listener.mParams);
                    stillCapture.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
                    stillCapture.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, calculateExposureCompensation(ev));
                    stillCapture.addTarget(newPreviewSurfaceAndCheck());
                    stillCapture.addTarget(mJpegImageReader.getSurface());
                    cameraSession.capture(stillCapture.build(), new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            Integer integer = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
                            Log.d(TAG, "onCaptureCompleted ev:" + integer);
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                            Log.e(TAG, "onCaptureFailed :" + failure.getReason() + ",," + failure.getFrameNumber() + ",," + failure.getSequenceId());
                            onError(new PiError(PiErrorCode.TAKE_PHOTO_CAPTURE_FAILED, "onCaptureFailed"));
                        }

                        @Override
                        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                            super.onCaptureSequenceAborted(session, sequenceId);
                            Log.d(TAG, "onCaptureSequenceAborted :" + sequenceId);
                            onError(new PiError(PiErrorCode.TAKE_PHOTO_CAPTURE_FAILED, "onCaptureSequenceAborted"));
                        }

                        @Override
                        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                            super.onCaptureBufferLost(session, request, target, frameNumber);
                            Log.d(TAG, "onCaptureBufferLost :" + frameNumber);
                            onError(new PiError(PiErrorCode.TAKE_PHOTO_CAPTURE_FAILED, "onCaptureBufferLost"));
                        }

                        private void onError(PiError error) {
                            if (mHdrImageProcessNor != null) {
                                mHdrImageProcessNor.onCaptureError(error);
                            }
                        }
                    }, mBackgroundHandler);
                } catch (CameraAccessException | IllegalStateException ex) {
                    ex.printStackTrace();
                    int code = PiErrorCode.CAMERA_SESSION_NOT_CREATE;
                    String message = "" + ex.getMessage();
                    if (ex instanceof CameraAccessException || message.contains("CameraDevice")) {
                        code = PiErrorCode.CAMERA_NOT_OPENED;
                    }
                    onTakePhotoError(listener, code);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    onTakePhotoError(listener, PiErrorCode.UN_KNOWN);
                }
            }

            private void onTakePhotoError(TakePhotoListener listener, int code) {
                if (mHdrImageProcessNor != null) {
                    mHdrImageProcessNor.aborted();
                }
                callTakePhotoError(listener, code);
                updatePreview();
            }
        });
    }

    private void takePhotoNorInner(TakePhotoListener listener) {
        CameraCaptureSession cameraSession = mCameraSession;
        final ImageProcess jpegImageProcess = new ImageProcess(listener, mJpegImageReader, false, mPiPano); // jpeg
        final ImageProcess rawImageProcess;
        if (PiPhotoFileFormat.jpg_raw.equals(listener.mParams.fileFormat) ||
                PiPhotoFileFormat.jpg_dng.equals(listener.mParams.fileFormat)) {
            // hdr时，raw 仅保存一张
//            rawImageProcess = new ImageProcess(listener, mRawImageReader, false); // raw
        } else {
//            rawImageProcess = null;
        }
        rawImageProcess = null;
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "takePhotoNorInner post run start.");
                CaptureRequest.Builder stillCapture;
                try {
                    stillCapture = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    Log.d(TAG, "takePhotoNorInner createCaptureRequest end.");
                } catch (CameraAccessException | IllegalStateException e) {
                    e.printStackTrace();
                    onTakePhotoError(listener, PiErrorCode.CAMERA_NOT_OPENED);
                    return;
                }
                stillCapture.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                setCaptureRequestParam(stillCapture, false);
                fillExtInfo(stillCapture, listener.mParams);
                stillCapture.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
                //
                stillCapture.addTarget(newPreviewSurfaceAndCheck());
                stillCapture.addTarget(mJpegImageReader.getSurface());
                if (null != rawImageProcess) {
                    stillCapture.addTarget(mRawImageReader.getSurface());
                }
                if (cameraSession == null) {
                    Log.e(TAG, "takePhoto err : mCameraSession is null !");
                    onTakePhotoError(listener, PiErrorCode.CAMERA_SESSION_NOT_CREATE);
                    return;
                }
                try {
                    cameraSession.capture(stillCapture.build(), new CameraCaptureSession.CaptureCallback() {
                        private boolean success = false;

                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            Log.d(TAG, "takePhoto,onCaptureCompleted");
                            if (!success && listener != null) {
                                success = true;
                                listener.onCapturePhotoEnd();
                            }
                            if (null != rawImageProcess) {
                                if (rawImageProcess.needDng()) {
                                    DngCreator dngCreator = new DngCreator(mCameraCharacteristics, result);
                                    rawImageProcess.setDngCreator(dngCreator);
                                }
                            }
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                            Log.e(TAG, "takePhoto,onCaptureFailed,reason:" + failure.getReason());
                            onTakePhotoError(listener, PiErrorCode.TAKE_PHOTO_CAPTURE_FAILED);
                        }
                    }, mBackgroundHandler);
                    Log.d(TAG, "takePhotoNorInner capture end.");
                } catch (CameraAccessException | IllegalStateException ex) {
                    Log.e(TAG, "takePhoto,ex:" + ex);
                    ex.printStackTrace();
                    int code = PiErrorCode.CAMERA_SESSION_NOT_CREATE;
                    String message = "" + ex.getMessage();
                    if (ex instanceof CameraAccessException || message.contains("CameraDevice")) {
                        code = PiErrorCode.CAMERA_NOT_OPENED;
                    }
                    onTakePhotoError(listener, code);
                }
            }

            private void onTakePhotoError(TakePhotoListener listener, int errorCode) {
                jpegImageProcess.aborted();
                if (rawImageProcess != null) {
                    rawImageProcess.aborted();
                }
                callTakePhotoError(listener, errorCode);
            }
        });
    }

    private void callTakePhotoError(TakePhotoListener listener, int code) {
        if (listener != null) {
            listener.dispatchTakePhotoComplete(code);
        }
        if (mPiPano != null) {
            mPiPano.setPanoEnabled(true);
        }
    }

    /**
     * 填充额外的请求信息，含：位置
     *
     * @param builder 捕获请求
     */
    @SuppressLint("MissingPermission")
    private void fillExtInfo(CaptureRequest.Builder builder, PhotoParams params) {
        Location location = null;
        if (params.latitude != 0 && params.longitude != 0) {
            location = new Location(LocationManager.GPS_PROVIDER);
            location.setLatitude(params.latitude);
            location.setLongitude(params.longitude);
            location.setAltitude(params.altitude);
        } else {
            LocationManager ls = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            if (mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    location = ls.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        builder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
    }

    private void checkImageReaderFormat(List<Surface> list, boolean forceInitImageReader) {
        boolean hasJpg = false;
        boolean hasRaw = false;
        for (int format : mPreviewImageReaderFormat) {
            if (format == ImageFormat.JPEG) {
                hasJpg = true;
            } else if (format == ImageFormat.RAW_SENSOR) {
                //hasRaw = true;
            }
        }
        Log.d(TAG, String.format("checkImageReaderFormat jpg[%b],raw[%b] ==> %s", hasJpg, hasRaw, mCameraEnvParams));
        if (hasJpg) {
            if (!forceInitImageReader) {
                mJpegImageReader = ImageReader.newInstance(5800, 2900, ImageFormat.JPEG, 2);
            } else {
                mJpegImageReader = ImageReader.newInstance(10920, 5460, ImageFormat.JPEG, 1);
            }
            list.add(mJpegImageReader.getSurface());
        }
        if (hasRaw) {
            mRawImageReader = ImageReader.newInstance(8112, 3040, ImageFormat.RAW_SENSOR, 1);
            list.add(mRawImageReader.getSurface());
        }
    }

    private static final class CameraCallbackWrap {
        PiCallback mPiCallback;
        int mState;

        public CameraCallbackWrap(PiCallback piCallback, int state) {
            mPiCallback = piCallback;
            mState = state;
        }

        @NonNull
        @Override
        public String toString() {
            return "CameraCallbackWrap{" +
                    "mPiCallback=" + mPiCallback +
                    ", mState=" + mState +
                    '}';
        }
    }

    long getFirstRecordSensorTimestamp() {
        return mFirstRecordSensorTimestamp < 0 ? 0 : mFirstRecordSensorTimestamp;
    }

    private static int calculateExposureCompensation(@PiExposureCompensation int exposureCompensation) {
        // 转换为接口曝光补偿值, 补偿范围*补偿步长.
        // PiExposureCompensation 定义的范围为[-4,+4] 改为索引值,
        // pano1/pano2 上 Compensation Range为[-2,+2], Compensation Step为1/6, Exposure Compensation值放大了2倍.
        return exposureCompensation * CONTROL_AE_EXPOSURE_COMPENSATION_STEP / 2;
    }
}
