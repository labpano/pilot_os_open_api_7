package com.pi.pano;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

public class OpenGLThread extends PiPano implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "JPiPano";

    /**
     * 每帧间隔时长
     */
    private long mPreviewFpsDuration = 1000 / mPreviewFps;
    /**
     * 请求全景预览
     */
    private boolean mReqPanoramaPreview = false;
    private int mNotSyncCount;

    @Override
    public void setPreviewFps(int fps) {
        super.setPreviewFps(fps);
        if (mPreviewFps > 0) {
            mPreviewFpsDuration = 1000 / mPreviewFps;
        } else {
            mPreviewFpsDuration = 0;
        }
    }

    public void setPanoramaPreview(boolean reqPanoramaPreview) {
        mReqPanoramaPreview = reqPanoramaPreview;
    }

    private long mLastLensDrawTimestamp = 0;
    private long mLastPreviewDrawTimestamp = -1;

    OpenGLThread(PiPanoListener piPanoListener, Context context, boolean isRenderFromCamera, int openCameraCount) {
        super(piPanoListener, context, isRenderFromCamera, openCameraCount);
    }

    private final Object mBaseTimeLock = new Object();
    private long mBaseTimestamp = 0;

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (mFrameAvailableTask.length == 1) {
            updateTexImage(surfaceTexture);
            updateEncoderSurface(surfaceTexture.getTimestamp());
        } else {
            if (mPiPanoListener.getClass() == StitchingThread.class ||
                    mPiPanoListener instanceof MediaPlayerSurfaceView) {
                updateTexImage(surfaceTexture);
                if (Math.abs(mFrameAvailableTask[0].getTimestamp() - mFrameAvailableTask[1].getTimestamp()) < Config.FRAME_SYNC_DELTA_TIME) {
                    updateEncoderSurface(mFrameAvailableTask[0].getTimestamp());
                }
            } else {
                long timestamp = surfaceTexture.getTimestamp();
                if (mFrameAvailableTask[0].getTimestamp() < mBaseTimestamp) {
                    mBaseTimestamp = mFrameAvailableTask[0].getTimestamp();
                }
                synchronized (mBaseTimeLock) {
                    if (timestamp > mBaseTimestamp + 1000000) {
                        if (mFrameAvailableTask[0].getTimestamp() > mBaseTimestamp + 1000000 &&
                                mFrameAvailableTask[1].getTimestamp() > mBaseTimestamp + 1000000) {
                            mBaseTimestamp = mFrameAvailableTask[0].getTimestamp();
                            Message.obtain(mHandler, MSG_PIPANO_ENCODER_SURFACE_UPDATE, mBaseTimestamp).sendToTarget();
                            mBaseTimeLock.notifyAll();
                        } else {
                            try {
                                mBaseTimeLock.wait(1000);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                Message.obtain(mHandler, MSG_PIPANO_FRAME_AVAILABLE, surfaceTexture).sendToTarget();
            }
        }
    }

    private void updateTexImage(SurfaceTexture surfaceTexture) {
        try {
            surfaceTexture.updateTexImage();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (FrameAvailableTask frameAvailableTask : mFrameAvailableTask) {
            if (surfaceTexture == frameAvailableTask.mSurfaceTexture) {
                frameAvailableTask.mCameraFrameCount++;
            }
        }
    }

    private void updateEncoderSurface(long timestamp) {
        if (mSkipFrameWithTakeHdr > 0) {
            Log.w(TAG, "skipFrameWithTakeHdr:" + mSkipFrameWithTakeHdr);
            mSkipFrameWithTakeHdr--;
            return;
        }
        if (!mPanoEnabled || !isCaptureCompleted) {
            return;
        }
        boolean isFrameSync = true;
        if (mFrameAvailableTask.length > 1) {
            long timestamp1 = mFrameAvailableTask[0].getTimestamp();
            long timestamp2 = mFrameAvailableTask[1].getTimestamp();
            long deltaTime = Math.abs(timestamp1 - timestamp2);
            isFrameSync = deltaTime < Config.FRAME_SYNC_DELTA_TIME;
            if (!isFrameSync) {
                mNotSyncCount++;
            }
//                    if (sDebug) {
//                        makeDebugTextureForSync(mNotSyncCount, deltaTime, isFrameSync);
//                    }
            if (!isFrameSync) {
                Log.e(TAG, String.format("frame is not sync [%.3f-%.3f] deltaTime[%.3f] not sync count[%d]",
                        timestamp1 / 1000000.0, timestamp2 / 1000000.0, deltaTime / 1000000.0, mNotSyncCount));
            }
        }

        //实时拼接时,双目要将boottime时间转为monotonic时间
        if (mPiPanoListener.getClass() == CameraSurfaceView.class) {
            long deltaTimeMsNs = SystemClock.elapsedRealtimeNanos() - System.nanoTime();
            timestamp -= deltaTimeMsNs;
        }

        mLastLensDrawTimestamp = timestamp;
        mPiPanoListener.onPiPanoEncoderSurfaceUpdate(this, timestamp, isFrameSync);
        mEncodeFrameCount++;
    }

    @SuppressLint("Recycle")
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_PIPANO_INIT:
                int openCameraCount = (int) msg.obj;
                createNativeObj(openCameraCount, sDebug, mCacheDir);
                mFrameAvailableTask = new FrameAvailableTask[openCameraCount];
                for (int i = 0; i < openCameraCount; ++i) {
                    FrameAvailableTask frameAvailableTask = new FrameAvailableTask();
                    frameAvailableTask.mSurfaceTexture = new SurfaceTexture(getTextureId(i));
                    //后拼接或只开一个camera,没有必要开多个线程处理多个onFrameAvailable同步
                    if (openCameraCount == 1 ||
                            mPiPanoListener.getClass() == StitchingThread.class ||
                            mPiPanoListener instanceof MediaPlayerSurfaceView) {
                        frameAvailableTask.mSurfaceTexture.setOnFrameAvailableListener(this, mHandler);
                    } else {
                        frameAvailableTask.mSurfaceTexture.setOnFrameAvailableListener(this,
                                frameAvailableTask.initHandler("FrameAvailableTask" + i));
                    }
                    mFrameAvailableTask[i] = frameAvailableTask;
                }
                mHasInit = true;
                mPiPanoListener.onPiPanoInit(this);
                mHandler.sendEmptyMessage(MSG_PIPANO_UPDATE);
                mHandler.sendEmptyMessageDelayed(MSG_PIPANO_UPDATE_PRE_SECOND, 1000);
                break;
            case MSG_PIPANO_UPDATE:
                if (mPanoEnabled && mPreviewFpsDuration > 0) {
                    mHandler.sendEmptyMessageDelayed(MSG_PIPANO_UPDATE, mPreviewFpsDuration);
                    final boolean hasChange = mLastPreviewDrawTimestamp != mLastLensDrawTimestamp;
                    if (hasChange) {
                        drawPreviewFrame(mReqPanoramaPreview, 0, true);
                        mLastPreviewDrawTimestamp = mLastLensDrawTimestamp;
                    } else {
                        drawPreviewFrame(mReqPanoramaPreview, 0, !mIsRenderFromCamera);
                    }
                    mPreviewFrameCount++;
                } else {
                    mHandler.sendEmptyMessageDelayed(MSG_PIPANO_UPDATE, 100);
                }
                break;
            case MSG_PIPANO_RELEASE:
                Log.d(TAG, "MSG_PIPANO_RELEASE start ");
                panoRelease();
                mHasInit = false;
                mNativeContext = 0;
                mPiPanoListener.onPiPanoDestroy(this);
                for (FrameAvailableTask task : mFrameAvailableTask) {
                    task.release();
                }
                getLooper().quit();

                PiCallback callback;
                if (msg.obj instanceof PiCallback) {
                    callback = (PiCallback) msg.obj;
                    callback.onSuccess();
                }
                Log.d(TAG, "MSG_PIPANO_RELEASE end");
                break;
            case MSG_PIPANO_ENABLED:
                mPanoEnabled = msg.arg1 > 0;
                if (!mPanoEnabled) {
                    drawPreviewFrame(mReqPanoramaPreview, 1, true);
                }
                break;
            case MSG_PIPANO_DETECTION:
                mDetectionEnabled = msg.arg1 > 0;
                if (mDetectionEnabled) {
                    nativeDetectionStart((OnAIDetectionListener) msg.obj);
                    Input.keepRotateDegreeOnReset(true);
                } else {
                    nativeDetectionStop();
                    Input.keepRotateDegreeOnReset(false);
                }
                break;
            case MSG_PIPANO_DETECTION_RESET:
                nativeDetectionReset();
                break;
            case MSG_PIPANO_LENS_PROTECTED:
                nativeSetLensProtectedEnable(msg.arg1 > 0);
                break;
            case MSG_PIPANO_UPDATE_PRE_SECOND:
                mHandler.sendEmptyMessageDelayed(MSG_PIPANO_UPDATE_PRE_SECOND, 1000);
                mPiPanoListener.onPiPanoEncodeFrame(mEncodeFrameCount);

                if (sDebug) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("fps p:").append(mPreviewFrameCount)
                            .append(" e:").append(mEncodeFrameCount).append(" c:");
                    for (FrameAvailableTask frameAvailableTask : mFrameAvailableTask) {
                        builder.append(frameAvailableTask.mCameraFrameCount).append(' ');
                    }

                    builder.append("\ncpu: ").append(ResourceAnalysisUtil.getCpuInfoString());
                    builder.append("\ngpu: ").append(ResourceAnalysisUtil.getGpuInfoString());
                    builder.append("\nmem: ").append(ResourceAnalysisUtil.getMemoryInfoString());
                    builder.append("\ntem: ").append(ResourceAnalysisUtil.getCpuTemperatureInfoString());
                    builder.append(" bat: ").append(ResourceAnalysisUtil.getBatteryInfoString());

                    makeDebugTexture(true, makePreviewDebugTexture(builder.toString()), 0, 0.7f, 0.5f, 0.2f);
                }

                mPreviewFrameCount = 0;
                mEncodeFrameCount = 0;
                for (FrameAvailableTask frameAvailableTask : mFrameAvailableTask) {
                    frameAvailableTask.mCameraFrameCount = 0;
                }
                break;
            case MSG_PIPANO_SET_ENCODE_SURFACE:
                Object surface = msg.obj;
                if (surface instanceof Surface) {
                    if (!((Surface) surface).isValid()) {
                        Log.e(TAG, "setSurface filter by !isValid()");
                        break;
                    }
                }
                switch (msg.arg1) {
                    case 0:
                        setPreviewSurface((Surface) msg.obj);
                        break;
                    case 1:
                        setEncodeVideoSurface((Surface) msg.obj);
                        break;
                    case 2:
                        setEncodeLiveSurface((Surface) msg.obj);
                        break;
                    case 3:
                        setPresentationSurface((Surface) msg.obj);
                        break;
                    case 4:
                        setEncodeVideoThumbSurface((Surface) msg.obj);
                        break;
                    case 5:
                        setEncodeScreenLiveSurface((Surface) msg.obj);
                        break;
                    case 6:
                        setEncodeVioSurface((Surface) msg.obj);
                        break;
                    case 7:
                        setEncodePhotoSurface((Surface) msg.obj);
                        break;
                    case 8:
                        setEncodeMemomotion0Surface((Surface) msg.obj);
                        break;
                    case 9:
                        setEncodeMemomotion1Surface((Surface) msg.obj);
                        break;
                }
                break;
            case MSG_PIPANO_TAKE_PHOTO:
                break;
            case MSG_PIPANO_SAVE_PHOTO:
                SavePhotoListener listener = (SavePhotoListener) msg.obj;
                if (listener.mThumbnail) {
                    savePicture(listener.mFilename, listener.mNewFilename, listener.mWidth, listener.mHeight, true);
                } else {
                    int ret = savePicture(listener.mFilename, listener.mNewFilename, listener.mWidth, listener.mHeight, false);
                    if (ret == 0) {
                        listener.onSavePhotoComplete();
                    } else {
                        listener.onSavePhotoFailed();
                    }
                }
                break;
            case MSG_PIPANO_CHANGE_CAMERA_RESOLUTION:
                ChangeResolutionListener resolutionListener = (ChangeResolutionListener) msg.obj;
                CameraEnvParams cameraEnvParams = PilotSDK.getCurCameraEnvParams();
                boolean drawBlurFrame = cameraEnvParams == null || resolutionListener.forceChange;
                if (!drawBlurFrame) {
                    boolean cameraChanged = !TextUtils.equals(resolutionListener.mCameraId,
                            cameraEnvParams.getCameraId());
                    boolean sizeChanged = resolutionListener.mWidth != cameraEnvParams.getWidth() ||
                            resolutionListener.mHeight != cameraEnvParams.getHeight();
                    boolean fpsChanged = false;
                    if (!PilotSDK.isLockDefaultPreviewFps()
                            && CameraEnvParams.CAPTURE_STREAM.equals(cameraEnvParams.getCaptureMode())) {
                        fpsChanged = cameraEnvParams.getFps() != resolutionListener.mFps;
                    }
                    drawBlurFrame = cameraChanged || sizeChanged || fpsChanged;
                }
                if (drawBlurFrame) {
                    drawPreviewFrame(mReqPanoramaPreview, 1, true);
                }
                setPanoEnabled(true);
                mPiPanoListener.onPiPanoChangeCameraResolution(resolutionListener);
                break;
            case MSG_PIPANO_SET_STABILIZATION_FILENAME:
                nativeStabSetFilenameForSave((String) msg.obj);
                break;
            case MSG_PIPANO_SLAME_START:
                SlamListener slamListener = (SlamListener) msg.obj;
                nativeSlamStart(slamListener.assetManager, slamListener.lenForCalMeasuringScale,
                        slamListener.showPreview, slamListener.useForImuToPanoRotation, slamListener);
                break;
            case MSG_PIPANO_SLAME_STOP:
                nativeSlamStop();
                break;
            case MSG_PIPANO_PARAM_RECALI:
                nativeSetParamCalculateEnable(msg.arg1, msg.arg2 == 1);
                break;
            case MSG_PIPANO_FRAME_AVAILABLE:
                updateTexImage((SurfaceTexture) msg.obj);
                break;
            case MSG_PIPANO_ENCODER_SURFACE_UPDATE:
                updateEncoderSurface((long) msg.obj);
                break;
            case MSG_PIPANO_RELOAD_WATERMARK:
                nativeReloadWatermark((boolean) msg.obj);
                break;
        }
        return false;
    }

    private void makeDebugTextureForSync(int notSyncCount, long oneDeltaTime, boolean oneIsFrameSync) {
        String text = "max delta: " + oneDeltaTime / 1000000.0 + "ms\n" + "not sync count: " + notSyncCount + "\n";
        makeDebugTexture(false,
                makeMediaDebugTexture(text, oneIsFrameSync ? Color.YELLOW : Color.RED), 0, 0.95f, 0.08f, 0.05f);
    }
}
