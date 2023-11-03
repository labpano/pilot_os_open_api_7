package com.pi.pano;

import android.content.Context;
import android.hardware.camera2.CaptureRequest;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiAntiMode;
import com.pi.pano.annotation.PiCameraId;
import com.pi.pano.annotation.PiExposureCompensation;
import com.pi.pano.annotation.PiExposureTime;
import com.pi.pano.annotation.PiLensCorrectionMode;
import com.pi.pano.annotation.PiWhiteBalance;
import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;
import com.pi.pano.wrap.ProParams;
import com.pi.pano.wrap.life.PanoSurfaceStateManager;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 拍摄使用的 camera pano view。
 */
class CameraSurfaceView extends PanoSurfaceView {
    private static final String TAG = "CameraSurfaceView";

    private CameraToTexture[] mCameraToTexture = new CameraToTexture[0];

    /**
     * 同步锁
     */
    private static final Object sLocks = new Object();

    /**
     * 持续采集捕获的Surface
     */
    private WeakReference<Surface> mCaptureSurfaceRef = null;

    /**
     * 是否锁定默认预览帧率
     */
    private boolean mLockDefaultPreviewFps = true;
    /**
     * 打开预览所需的 imageReader格式（用于拍照时设置）
     */
    private int[] mPreviewImageReaderFormat;
    /**
     * 锁定预览帧率时，记录下上次 绘制畸变画面的时间
     */
    private long mLastDrawLensTimeWithLockPfs;
    private final CameraSyncFrameHelper mSyncFrameHelper = new CameraSyncFrameHelper();
    private final AtomicBoolean mStopCaptureTag = new AtomicBoolean(false);

    private ProParams mProParams;

    public CameraSurfaceView(Context context, int openCameraCount) {
        this(context, null, openCameraCount);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs, int openCameraCount) {
        super(context, attrs, openCameraCount);
        mLensCorrectionMode = PiLensCorrectionMode.PANO_MODE_2;
    }

    void setLensCorrectionMode(@PiLensCorrectionMode int mode) {
        mLensCorrectionMode = mode;
    }

    @Override
    public void onPiPanoInit(@NonNull PiPano pano) {
        Log.i(TAG, "onPiPanoInit:" + pano);
        mCameraToTexture = new CameraToTexture[pano.mFrameAvailableTask.length];
        for (int i = 0; i < pano.mFrameAvailableTask.length; ++i) {
            mCameraToTexture[i] = new CameraToTexture(mContext, i, pano.mFrameAvailableTask[i].mSurfaceTexture, pano);
        }
        super.onPiPanoInit(pano);
    }

    @Override
    public void onPiPanoChangeCameraResolution(@NonNull ChangeResolutionListener listener) {
        int textureLength = mCameraToTexture.length;
        Log.i(TAG, "onPiPanoChangeCameraResolution " + listener.mWidth + "*" + listener.mHeight + "*"
                + listener.mFps + "===>" + textureLength);
        int length = textureLength - 1;
        PiCallback commCallback = mSyncFrameHelper.createSyncFrameCallback(mCameraToTexture,
                new CameraSyncFrameHelper.SimpleCallBackAdapter(listener));
        for (int i = length; i >= 0; --i) {
            CameraToTexture cameraToTexture = mCameraToTexture[i];
            cameraToTexture.setLockDefaultPreviewFps(mLockDefaultPreviewFps);
            cameraToTexture.setPreviewImageReaderFormat(mPreviewImageReaderFormat);
            int cameraBehavior = cameraToTexture.checkCameraBehavior(
                    listener.mCameraId.isEmpty() || (PiCameraId.ASYNC_DOUBLE + "").equals(listener.mCameraId)
                            ? i + "" : listener.mCameraId,
                    listener.mWidth, listener.mHeight, listener.mFps, listener.forceChange);
            Log.d(TAG, "setChangeResolutionCallback : " + commCallback);
            if (listener.mProParams == null) {
                listener.mProParams = mProParams;
            } else {
                mProParams = listener.mProParams;
            }
            cameraToTexture.doCameraBehavior(cameraBehavior, listener.mProParams, commCallback);
        }
    }

    @Override
    public void onPiPanoEncoderSurfaceUpdate(@NonNull PiPano pano, long timestamp, boolean isFrameSync) {
        if (isFrameSync) {
            if (mLockDefaultPreviewFps) {
                //锁定预览帧率为30fps时,确认2帧间隔时间 > (1s/30)
                if (timestamp - mLastDrawLensTimeWithLockPfs >= 33_000_000L) {
                    pano.drawLensCorrectionFrame(mLensCorrectionMode, true, timestamp, false);
                    mLastDrawLensTimeWithLockPfs = timestamp;
                }
            } else {
                pano.drawLensCorrectionFrame(mLensCorrectionMode, true, timestamp, false);
            }
        }
    }

    @Override
    public void onPiPanoDestroy(@NonNull PiPano pano) {
        Log.i(TAG, "onPiPanoDestroy:" + pano);
        while (null != mCaptureSurfaceRef && mCaptureSurfaceRef.get() != null) {
            synchronized (sLocks) {
                try {
                    Log.d(TAG, "releaseCamera wait");
                    long timeOut = SystemPropertiesProxy.getLong(Config.PERSIST_DEV_TIMEOUT_CAMERA_RELEASE,
                            Config.MAX_RELEASE_CAMERA_TIMEOUT);
                    sLocks.wait(timeOut);
                    mCaptureSurfaceRef = null;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "releaseCamera wait end");
            }
        }
        //释放时,标记 Capture数据不可用
        pano.isCaptureCompleted = false;
        Log.d(TAG, "releaseCamera end");
        mPiPano = null;
        for (CameraToTexture c : mCameraToTexture) {
            c.releaseCamera();
        }
        mProParams = mCameraToTexture[0].getProParams();
        onPanoReleaseCallback();
        mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.DESTROY_FINISHED);
    }

    void takePhoto(@NonNull final TakePhotoListener listener) {
        if (mPiPano != null) {
            PiPano.clearImageList();
            listener.mParams.saveConfig(isLensProtected());
            if (listener.mParams.isHdr()) {
                mPiPano.setPanoEnabled(false);
            }
            listener.onTakePhotoStart(0);
            mCameraToTexture[0].takePhoto(listener, true);
            if (listener.mParams.makeSlamPhotoPoint) {
                mPiPano.makeSlamPhotoPoint(listener.mParams.obtainBasicName());
            }
        } else {
            Log.e(TAG, "takePhoto PiPano is null");
            listener.dispatchTakePhotoComplete(PiErrorCode.ALREADY_RELEASE);
        }
    }

    public void setProParams(ProParams params, PiCallback callback) {
        for (CameraToTexture c : mCameraToTexture) {
            c.setProParams(params, callback, 0);
        }
        this.mProParams = getProParams();
    }

    /**
     * 是否处理hdr拍照中。
     */
    public void setInPhotoHdr(boolean inHdr) {
        if (inHdr) {
            for (CameraToTexture c : mCameraToTexture) {
                c.setSceneMode(CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT);
            }
        } else {
            for (CameraToTexture c : mCameraToTexture) {
                c.setSceneMode(-1);
            }
        }
    }

    public ProParams getProParams() {
        return mCameraToTexture[0].getProParams();
    }

    void setAntiMode(String value) {
        for (CameraToTexture c : mCameraToTexture) {
            c.setAntiMode(value);
        }
    }

    void setLockDefaultPreviewFps(boolean value) {
        this.mLockDefaultPreviewFps = value;
        for (CameraToTexture c : mCameraToTexture) {
            c.setLockDefaultPreviewFps(value);
        }
    }

    void setPreviewImageReaderFormat(int[] values) {
        mPreviewImageReaderFormat = values;
        for (CameraToTexture c : mCameraToTexture) {
            c.setPreviewImageReaderFormat(values);
        }
    }

    boolean isLargePreviewSize() {
        if (mCameraToTexture.length == 1) {
            return true;
        }
        for (CameraToTexture c : mCameraToTexture) {
            CameraEnvParams params = c.getCameraEnvParams();
            //当preview分辨率大于2000的时候,录制实时拼接视频,否则录制为单独两个视频用于后处理
            if (params != null && params.getWidth() >= 2000) {
                return true;
            }
        }
        return false;
    }

    public boolean isLockDefaultPreviewFps() {
        return mLockDefaultPreviewFps;
    }

    CameraEnvParams getCurCameraEnvParams() {
        for (CameraToTexture c : mCameraToTexture) {
            return c.getCameraEnvParams();
        }
        return null;
    }

    int getPreviewFps() {
        CameraEnvParams params = getCurCameraEnvParams();
        return params == null ? 0 : params.getFps();
    }

    int getPreviewWidth() {
        CameraEnvParams params = getCurCameraEnvParams();
        return params == null ? 0 : params.getWidth();
    }

    int getPreviewHeight() {
        CameraEnvParams params = getCurCameraEnvParams();
        return params == null ? 0 : params.getHeight();
    }

    String getCameraId() {
        CameraEnvParams params = getCurCameraEnvParams();
        return params == null ? null : params.getCameraId();
    }

    /**
     * mCameraToTexture数量（等同于当前同时打开镜头数量）
     */
    int getCameraToTextureCount() {
        return mCameraToTexture == null ? 0 : mCameraToTexture.length;
    }

    void startPreview(PiCallback callback) {
        for (CameraToTexture c : mCameraToTexture) {
            c.stopPreview();
        }
        int length = mCameraToTexture.length;
        PiCallback syncFrameCallback = mSyncFrameHelper.createSyncFrameCallback(mCameraToTexture,
                new CameraSyncFrameHelper.SimpleCallBackAdapter(callback));
        for (int i = length - 1; i >= 0; --i) {
            CameraToTexture c = mCameraToTexture[i];
            c.startPreview(syncFrameCallback, false);
        }
    }

    void startPreviewWithTakeLargePhoto(@NonNull PiCallback callback, int skipFrame) {
        mCameraToTexture[0].startPreview(new PiCallback() {
            @Override
            public void onSuccess() {
                mCameraToTexture[0].updatePreview(callback, skipFrame);
            }

            @Override
            public void onError(PiError error) {
                callback.onError(error);
            }
        }, true);
    }

    void startCapture(Surface[] surface, boolean previewEnabled) {
        startCapture(surface, previewEnabled, new SimplePiCallback(), null);
    }

    /**
     * @param surface        捕获数据的surface
     * @param previewEnabled 预览是否可用
     * @param callback       回调
     * @param cSize          捕获中的预览分辨率
     */
    boolean startCapture(Surface[] surface, boolean previewEnabled, @NonNull PiCallback callback, @Nullable int[] cSize) {
        mPiPano.setPanoEnabled(previewEnabled);
        //要做到硬同步,要先关掉两个预览流
        boolean is4k60Fps = CameraPreview.CAMERA_PREVIEW_940_940_60_v == cSize;
        for (CameraToTexture cameraToTexture : mCameraToTexture) {
            if (is4k60Fps) {
                cameraToTexture.closeCamera();
            } else {
                cameraToTexture.stopPreview();
            }
        }
        //TODO: 必须确认停止预览后,才能开始录像,否则会打开三个stream,这里先用sleep,后面要改进
        if (!is4k60Fps) {
            SystemClock.sleep(1500);
        }
        //先打开从摄像头camera1,然后再打开主摄像头camera0
        Log.i(TAG, "startCapture sleep end ====> " + mStopCaptureTag.get());
        if (mStopCaptureTag.get()) {
            callback.onError(new PiError(PiErrorCode.UN_KNOWN, "already stopCapture by self after startCapture "));
            mStopCaptureTag.set(false);
            return false;
        }
        final int length = mCameraToTexture.length;
        PiCallback frameCallback = mSyncFrameHelper.createSyncFrameCallback(mCameraToTexture,
                new CameraSyncFrameHelper.SimpleCallBackAdapter(callback), false);
        for (int i = length - 1; i >= 0; --i) {
            CameraToTexture c = mCameraToTexture[i];
            if (is4k60Fps) {
                c.reopenCameraInCapture(surface[i], frameCallback, cSize);
            } else {
                c.startCapture(surface[i], frameCallback, cSize);
            }
        }
        mCaptureSurfaceRef = new WeakReference<>(surface[0]);
        return true;
    }

    void reopenCameraInCapture(Surface[] surfaces, boolean previewEnabled,
                               @Nullable int[] cSize, @NonNull PiCallback callback) {
        mPiPano.setPanoEnabled(previewEnabled);
        for (CameraToTexture cameraToTexture : mCameraToTexture) {
            cameraToTexture.releaseCamera();
        }
        //TODO: 必须确认停止预览后,才能开始录像,否则会打开三个stream,这里先用sleep,后面要改进
        SystemClock.sleep(500);
        final int length = mCameraToTexture.length;
        PiCallback frameCallback = mSyncFrameHelper.createSyncFrameCallback(mCameraToTexture,
                new CameraSyncFrameHelper.SimpleCallBackAdapter(callback), false);
        for (int i = length - 1; i >= 0; --i) {
            CameraToTexture c = mCameraToTexture[i];
            c.reopenCameraInCapture(surfaces[i], frameCallback, cSize);
        }
        mCaptureSurfaceRef = new WeakReference<>(surfaces[0]);
    }

    int stopCapture(boolean sync, boolean startPreview) {
        try {
            if (startPreview) {
                for (CameraToTexture c : mCameraToTexture) {
                    c.stopCapture(sync);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mCaptureSurfaceRef = null;
            synchronized (sLocks) {
                sLocks.notifyAll();
            }
        }
        Log.d(TAG, "stopCapture ==> " + sync + "," + startPreview + ",ref:" + mCaptureSurfaceRef);
        mPiPano.setPanoEnabled(true);
        restoreStabilizationMediaHeightInfo();
        Log.i(TAG, "stop record success");
        return 0;
    }

    int stopCaptureByVideo(PiCallback callback) {
        int length = mCameraToTexture.length;
        PiCallback syncFrameCallback = mSyncFrameHelper.createSyncFrameCallback(mCameraToTexture,
                new CameraSyncFrameHelper.SimpleCallBackAdapter(callback), false);
        for (int i = length - 1; i >= 0; --i) {
            CameraToTexture c = mCameraToTexture[i];
            c.stopCaptureByVideo(syncFrameCallback);
        }
        restoreStabilizationMediaHeightInfo();
        return 0;
    }

    public void stopCaptureTag(boolean enable) {
        mStopCaptureTag.set(enable);
        Log.i(TAG, "stopCaptureTag ==>" + mStopCaptureTag.get() + "," + enable);
    }

    int stopCaptureBySplit() {
        Log.i(TAG, "stopCaptureBySplit start ");
        mPiPano.setPanoEnabled(false);
        try {
            for (CameraToTexture c : mCameraToTexture) {
                c.stopPreview();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mCaptureSurfaceRef = null;
            synchronized (sLocks) {
                sLocks.notifyAll();
            }
        }
        Log.i(TAG, "stopCaptureBySplit end ");
        return 0;
    }

    long getFirstRecordSensorTimestamp(int index) {
        if (index >= mCameraToTexture.length) return 0;
        return mCameraToTexture[index].getFirstRecordSensorTimestamp();
    }

    /**
     * 处于持续捕获中（录像或直播）
     */
    boolean isInCapture() {
        return null != mCaptureSurfaceRef && mCaptureSurfaceRef.get() != null;
    }

    public void setCommCameraCallback(PiCallback callback) {
        Log.d(TAG, "setCommCameraCallback cameraSize:" + mCameraToTexture.length + "," + callback);
        for (CameraToTexture c : mCameraToTexture) {
            c.setCommCameraCallback(callback);
        }
    }

    public boolean isCameraPreviewing() {
        boolean result = false;
        for (CameraToTexture c : mCameraToTexture) {
            result = c.isCameraPreviewing();
            if (!result) {
                return false;
            }
        }
        return result;
    }
}