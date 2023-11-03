package com.pi.pano;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiEncodeSurfaceType;

import java.io.File;

public class StitchingThread extends Thread implements PiPano.PiPanoListener {
    private final String TAG = "StitchingThread";

    /**
     * 删除之前已拼接文件错误
     */
    private static final int STITCH_ERROR_DELETE_STITCHED = 0x100;

    /**
     * FrameExtractor错误
     */
    private static final int STITCH_ERROR_FRAME_EXTRACTOR = 0x200;

    /**
     * StitchingRecorder int错误
     */
    private static final int STITCH_ERROR_RECORDER_INIT = 0x300;

    /**
     * StitchingRecorder start错误
     */
    private static final int STITCH_ERROR_RECORDER_START = 0x301;

    private final Context mContext;
    private PiPano mPiPano;
    private StitchingUtil.Task mCurrentTask;
    private StitchingRecorder mStitchingRecorder;
    private long mStartTime;
    private final StitchingUtil mStitchingUtil;
    private final StitchingNative mStitchingNative;

    /**
     * 拼接后帧率与原视频帧率比值
     */
    private float mFpsRatio = -1;
    private int mLastFrameCount = -1;
    private int mSurfaceUpdateCount = 0;
    private boolean mUseFlow;

    private static final Object mDecodeLock = new Object();

    StitchingThread(Context context, StitchingUtil stitchingUtil) {
        mContext = context;
        mStitchingUtil = stitchingUtil;
        mStitchingNative = new StitchingNative();
    }

    private void setErrorCode(int code, String message) {
        mCurrentTask.mErrorCode = code;
        Log.e(TAG, "Stitch error[" + Integer.toHexString(code) + "]:" + message);
    }

    private void startTask() {
        mCurrentTask.changeState(StitchingUtil.StitchState.STITCH_STATE_STARTING);
        Log.i(TAG, "startTask file: " + mCurrentTask.params.srcDir.getName());
        mCurrentTask.mErrorCode = 0;

        String stitchingPath = StitchingUtil.stitchingPath(mCurrentTask.params.srcDir, mCurrentTask.params.targetDir, false);
        Log.d(TAG, "startTask ing file :" + stitchingPath);

        File stitchedVideo = new File(stitchingPath);
        //删掉文件夹中原来的stitching.mp4
        if (stitchedVideo.exists()) {
            if (!stitchedVideo.delete()) {
                setErrorCode(STITCH_ERROR_DELETE_STITCHED, "delete stitched file error");
                return;
            }
        }
        mPiPano = new OpenGLThread(this, mContext,
                false, createCameraCount(mCurrentTask.params.srcDir));
        while (!mPiPano.mHasInit) {
            SystemClock.sleep(5);
        }
        // 保护镜
        mPiPano.setLensProtected(mCurrentTask.params.lensProtected);
        File stabilizationFile = new File(mCurrentTask.params.srcDir.getPath() + "/stabilization");
        mPiPano.useGyroscope(stabilizationFile.exists(), mCurrentTask.params.srcDir.getPath());
        //setStitchingConfig();
        int ret = mStitchingNative.startStitching(mCurrentTask.params.srcDir.getAbsolutePath(), mPiPano, false);
        if (ret != 0) {
            setErrorCode(ret, "frameExtractor init error");
            return;
        }
        int interval = Math.round(mCurrentTask.params.fps / 10f);
        Log.d(TAG, "setParamReCaliEnable :" + interval);
        mPiPano.setParamReCaliEnable(interval, true);
        //encorder
        mStitchingRecorder = new StitchingRecorder();
        Surface surface = mStitchingRecorder.init(mCurrentTask.params);
        if (surface == null) {
            setErrorCode(STITCH_ERROR_RECORDER_INIT, "stitchingRecorder init error");
            return;
        }
        mPiPano.setEncodeSurface(surface, PiEncodeSurfaceType.RECORD);
        if (!mStitchingRecorder.start()) {
            setErrorCode(STITCH_ERROR_RECORDER_START, "stitchingRecorder start error");
        }
    }


    private boolean processNextTask() {
        release();
        mCurrentTask = mStitchingUtil.getNextTask();
        if (mCurrentTask == null) {
            Log.i(TAG, "processNextTask all files finish or pause");
            return false;
        }
        if (mCurrentTask.getStitchState() == StitchingUtil.StitchState.STITCH_STATE_PAUSING) {
            return false;
        }
        startTask();
        if (mCurrentTask.mErrorCode == 0) {
            mStartTime = System.currentTimeMillis();
            if (mCurrentTask.getStitchState() == StitchingUtil.StitchState.STITCH_STATE_PAUSING) {
                release();
                return false;
            }
            mCurrentTask.changeState(StitchingUtil.StitchState.STITCH_STATE_START);
            return true;
        } else {
            mCurrentTask.changeState(StitchingUtil.StitchState.STITCH_STATE_ERROR);
            return false;
        }
    }

    private void release() {
        Log.i(TAG, "stitching util release");
        if (mStitchingRecorder != null) {
            mStitchingRecorder.stop(mCurrentTask.mState == StitchingUtil.StitchState.STITCH_STATE_PAUSING);
            mStitchingRecorder = null;
        }
        mStitchingNative.deleteFrameExtractor();
        if (mCurrentTask != null) {
            mCurrentTask.deleteStitchingPauseFile();
            if (mCurrentTask.mState == StitchingUtil.StitchState.STITCH_STATE_STOPPING) {
                mCurrentTask.changeState(StitchingUtil.StitchState.STITCH_STATE_STOP);
                mCurrentTask.deleteStitchTask();
            } else if (mCurrentTask.mState == StitchingUtil.StitchState.STITCH_STATE_PAUSING) {
                mCurrentTask.changeState(StitchingUtil.StitchState.STITCH_STATE_PAUSE);
            }
        }
        mCurrentTask = null;
        if (mPiPano != null) {
            mPiPano.release(null);
            mPiPano = null;
        }
    }

    @Override
    public void run() {
        while (true) {
            mStitchingUtil.checkHaveNextTask();
            if (processNextTask()) {
                StitchVideoParams params = mCurrentTask.params;
                long during = StitchingUtil.getDuring(params.srcDir + "/0.mp4");
                //计算上次暂停的时间,如果是拼接视频,那么直接计算上次暂停的时间
                //如果是拼接视频中某一帧的图像,这个暂停时间指的是该图像在视频中的时间戳
                long pauseTimeStamp = -1;
                if (mStitchingRecorder != null) {
                    pauseTimeStamp = mStitchingRecorder.getPauseTimeStamp();
                }
                Log.d(TAG, "nextTask check dur:" + during + "pauseTime:" + pauseTimeStamp);
                //如果时暂停重新开始,那么StitchingFrameExtractor要seek到上次停止的时间戳的前一个keyframe
                if (pauseTimeStamp != -1) {
                    mStitchingNative.seekToPreviousKeyFrame(pauseTimeStamp);
                }
                if (params.finishStitchTimeUs > 0 && params.finishStitchTimeUs < during) {
                    during = params.finishStitchTimeUs;
                }
                mUseFlow = params.useFlow;
                initFpsConfig(params);
                while (mCurrentTask.getStitchState() == StitchingUtil.StitchState.STITCH_STATE_START) {
                    if (!mStitchingNative.extractorOneFrame()) {
                        //拼接完成
                        Log.i(TAG, "Process finish video: " + params.srcDir.getName() +
                                " take time: " + (System.currentTimeMillis() - mStartTime));
                        mCurrentTask.changeState(StitchingUtil.StitchState.STITCH_STATE_STOPPING);
                        break;
                    }
                    if (mStitchingRecorder.isEndStitchByError()) {
                        Log.w(TAG, "Process finish video : isEndStitchByError");
                        mCurrentTask.changeState(StitchingUtil.StitchState.STITCH_STATE_ERROR);
                        break;
                    }
                    //只有当前的时间戳大于上次暂停的时间戳,这一帧才会被编码
                    boolean renderEncodeFrame = mStitchingNative.getExtractorSampleTime(0) > pauseTimeStamp;
                    mStitchingNative.decodeOneFrame(renderEncodeFrame);
                    if (renderEncodeFrame) {
                        synchronized (mDecodeLock) {
                            try {
                                mDecodeLock.wait(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    long curTime = mStitchingNative.getExtractorSampleTime(0);
                    float pro = curTime * 100.0f / during;
                    if (params.finishStitchTimeUs > 0 && curTime >= params.finishStitchTimeUs) {
                        Log.i(TAG, "run nextTask end by endStitchTime ========> ");
                        mCurrentTask.changeState(StitchingUtil.StitchState.STITCH_STATE_STOPPING);
                        break;
                    }
                    if (pro > mCurrentTask.mProgress) {
                        mCurrentTask.mProgress = pro;
                    }
                    if (mCurrentTask.mProgress > 100) {
                        mCurrentTask.mProgress = 100;
                    }
                    if (mStitchingUtil.mStitchingListener != null &&
                            mCurrentTask.getStitchState() == StitchingUtil.StitchState.STITCH_STATE_START) {
                        mStitchingUtil.mStitchingListener.onStitchingProgressChange(mCurrentTask);
                    }
                }
                Log.v(TAG, "process next task finally");
                release();
            }
        }
    }

    @Override
    public void onPiPanoInit(@NonNull PiPano pano) {
    }

    @Override
    public void onPiPanoChangeCameraResolution(@NonNull ChangeResolutionListener listener) {
    }

    @Override
    public void onPiPanoEncoderSurfaceUpdate(@NonNull PiPano pano, long timestamp, boolean isFrameSync) {
//        Log.i(TAG, "drawLensCorrectionFrame :" + mFpsRatio + ",count:" + mSurfaceUpdateCount + "," + timestamp);
        synchronized (mDecodeLock) {
            mDecodeLock.notifyAll();
        }
        if (isFrameSync) {
            if (mFpsRatio == -1) {
                pano.drawLensCorrectionFrame(0x1111, true, timestamp, mUseFlow);
                return;
            }
            mSurfaceUpdateCount++;
            int frameCount = (int) (mSurfaceUpdateCount * mFpsRatio);
            if (mLastFrameCount != frameCount) {
                pano.drawLensCorrectionFrame(0x1111, true, timestamp, mUseFlow);
                mLastFrameCount = frameCount;
            }
        }
    }

    @Override
    public void onPiPanoDestroy(@NonNull PiPano pano) {
    }

    @Override
    public void onPiPanoEncodeFrame(int count) {
    }

    private int createCameraCount(File srcDir) {
        if (srcDir.isDirectory()) {
            Size videoSize = Utils.getVideoSize(srcDir + "/0.mp4");
            if (videoSize != null && videoSize.getWidth() != 0
                    && videoSize.getWidth() == videoSize.getHeight() * 2) {
                return 1;
            }
        }
        return 2;
    }

    private void initFpsConfig(StitchVideoParams params) {
        mSurfaceUpdateCount = 0;
        if (params.srcFps != -1 && params.srcFps > params.fps) {
            mFpsRatio = params.fps / params.srcFps;
            mLastFrameCount = -1;
        } else {
            mFpsRatio = -1;
        }
    }

}
