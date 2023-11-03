package com.pi.pilot.pushsdk;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.pi.pano.IPushManager;
import com.pi.pano.PiPanoUtils;
import com.pi.pano.PilotLib;
import com.pi.pano.annotation.PiEncodeSurfaceType;
import com.pi.pano.annotation.PiVideoEncode;
import com.pi.pano.wrap.IMetadataCallback;
import com.pi.picommonlib.PiErrorCode;
import com.pi.piencoder.ext.IAudioEncoderExt;
import com.pi.pipusher.IPusher;
import com.pi.pipusher.IPusherStateListerner;
import com.pi.pipusher.PusherFactory;

public class PushManagerImpl implements IPushManager {
    private static final String TAG = PushManagerImpl.class.getSimpleName();
    private static final int timeoutMillSec = 5_000;

    private IPushManager.IParams mPushParam;
    private IPushManager.IPushListener mListener;
    private IPusher mPusher;
    private Surface mPushSurface;
    private final Handler mHandler;

    public PushManagerImpl() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    private final IAudioEncoderExt mAudioSourceExt = new IAudioEncoderExt() {
        @Override
        public void config(int i, int i1, int i2) {
            if (mPushParam.getEncoderExt() != null) {
                mPushParam.getEncoderExt().config(i, i1, i2);
            }
        }

        @Override
        public int decodeProcess(byte[] bytes, int i, byte[] bytes1) {
            return mPushParam.getEncoderExt() == null ? 0 :
                    mPushParam.getEncoderExt().decodeProcess(bytes, i, bytes1);
        }

        @Override
        public void release() {
            if (mPushParam.getEncoderExt() != null) {
                mPushParam.getEncoderExt().release();
            }
        }
    };

    @Override
    public void prepare(IParams params, IPushListener pushListener) {
        this.mPushParam = params;
        this.mListener = pushListener;
    }

    @Override
    public void startPush() {
        if (null != mPusher) {
            throw new RuntimeException("already startPush!");
        }
        Log.d(TAG, "startPush," + mPushParam);
        //创建推流对象
        mPusher = PusherFactory.createPusher();
        // 边推边录文件
        if (!TextUtils.isEmpty(mPushParam.getRecordPath()) && mPushParam.getRecordBlockDuration() > 0) {
            mPusher.addOutputFile(IPusher.OUTPUT_TYPE_MP4, mPushParam.getRecordPath(), mPushParam.getRecordBlockDuration());
        }
        //设置视频参数
        String videoEncoderId = PiVideoEncode.h_265.equals(mPushParam.getVideoEncode()) ?
                IPusher.ENCODER_ID_H265 : IPusher.ENCODER_ID_H264;
        mPusher.addVideoTrackInfo(videoEncoderId, mPushParam.getVideoSize().getWidth(),
                mPushParam.getVideoSize().getHeight(),
                mPushParam.getVideoFps(), mPushParam.getVideoBitrate());
        //设置音频参数
        int audioChannelNum = 2;
        int audioSampleRate = 48_000;
        int audioBitsPerSample = 16;
        int audioBitrate = 128_000;
        mPusher.addAudioTrackInfo(IPusher.AUDIO_SOURCE_ID_MIC,
                IPusher.ENCODER_ID_AAC,
                audioSampleRate, audioChannelNum, audioBitsPerSample, audioBitrate,
                mPushParam.getEncoderExt() == null ? null : mAudioSourceExt);
        //设置编码回调
        mPusher.setEncodeVideoListerner((s, surface) -> {
            Log.d(TAG, "updateSurface =======> " + s + ",,," + surface + ",old :" + mPushSurface);
            if (null == mPushSurface || surface != mPushSurface) {
                setSurface(surface);
            }
            return 0;
        });
        //视频pts补偿,fix 音画同步
        mPusher.setOffestPts(350);
        String protocol = mPushParam.getProtocol() == IProtocol.RTSP ?
                IPusher.OUTPUT_TYPE_RTSP : IPusher.OUTPUT_TYPE_RTMP;
        if (!TextUtils.isEmpty(mPushParam.getUserName())  && !TextUtils.isEmpty(mPushParam.getUserPassword())) {
            mPusher.addOutputWithAuth(protocol, mPushParam.getPushUrl(), timeoutMillSec,
                    mPushParam.getUserName(), mPushParam.getUserPassword());
            Log.d(TAG, "startPusher,push has auth!");
        } else {
            mPusher.addOutput(protocol, mPushParam.getPushUrl(), timeoutMillSec);
        }
        //启动推流
        mPusher.start(new _InternalPusherStateListener());
    }

    @Override
    public void restartPush() {
        if (null == mPusher) {
            throw new RuntimeException("not startPush or stopped!");
        }
        Log.d(TAG, "restartPush");
        mPusher.reconnectServer();
    }

    @Override
    public void resumePush() {
        if (null == mPusher) {
            throw new RuntimeException("not startPush or stopped!");
        }
        Log.d(TAG, "resumePush");
        bindSurface(mPushSurface);
        mPusher.resume();
        notifyPushResume();
    }

    @Override
    public void pausePush() {
        if (null == mPusher) {
            throw new RuntimeException("not startPush or stopPush!");
        }
        Log.d(TAG, "pausePush");
        bindSurface(null);
        mPusher.pause();
        mPusher.cleanResource();
        notifyPushPause();
    }

    @Override
    public void stopPush() {
        if (null == mPusher) {
            throw new RuntimeException("already stopPush!");
        }
        Log.d(TAG, "stopPush");
        setSurface(null);
        try {
            mPusher.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mPushSurface = null;
        mPusher = null;
    }

    @Override
    public void updateRecordPath(String filePath) {
        if (mPusher != null) {
            mPusher.setOutputPath(filePath);
        }
    }

    @Override
    public long getRealBitrate() {
        if (mPusher != null) {
            long speed = mPusher.getCurSendSpeed();
            if (speed >= 0) {
                return speed;
            }
            Log.e(TAG, "getSendSpeed err :" + speed);
        }
        Log.e(TAG, "getSendSpeed err : null");
        return 0;
    }

    @Override
    public int getNetworkQuality() {
        if (mPusher != null) {
            return mPusher.getNetQualityJudge();
        }
        Log.e(TAG, "getNetworkQuality err : null");
        return PushNetworkQuality.normal;
    }

    private synchronized void setSurface(Surface pushSurface) {
        if (null != mPusher) {
            mPushSurface = pushSurface;
            bindSurface(mPushSurface);
        }
    }

    private synchronized void bindSurface(@Nullable Surface surface) {
        if (!PilotLib.self().isDestroy()) {
            if (mPushParam.isPanorama()) {
                PilotLib.preview().setEncodeInputSurface(surface, PiEncodeSurfaceType.LIVE);
            } else {
                PilotLib.preview().setEncodeInputSurface(surface, PiEncodeSurfaceType.SCREEN_CAPTURE);
            }
        } else {
            Log.e(TAG, "pano view isn't load.");
        }
    }

    private class _InternalPusherStateListener implements IPusherStateListerner {
        @Override
        public void notifyPusherState(PusherState state, int errorCode, String message) {
            Log.d(TAG, "notifyPusherState,state:" + state + ",errorCode:" + errorCode + ",message:" + message);
            if ((errorCode == PiErrorCode.SUCCESS)) {
                if (state == PusherState.PUSHER_STATE_RUNNING) {
                    notifyPushStart();
                }
            } else if (errorCode == PiErrorCode.INIT_SUCCESS) {
                notifyPushPrepare();
            } else if (errorCode == PiErrorCode.RECORD_FILE_NAME) {
                notifyPushPathSuccess(message);
            } else if (errorCode < 0) {
                notifyPushError(errorCode, message);
            } else {
                if (errorCode == PiErrorCode.PUSHER_EXITED) {
                    notifyPushStop();
                }
            }
        }
    }

    protected void notifyPushStart() {
        Log.d(TAG, "notifyPushStart");
        if (null != mListener) {
            mHandler.post(() -> mListener.onPushStart());
        }
    }

    protected void notifyPushPrepare() {
        Log.d(TAG, "notifyPushPrepare");
        if (null != mListener) {
            mHandler.post(() -> mListener.onPushPrepare());
        }
    }

    protected void notifyPushResume() {
        Log.d(TAG, "notifyPushResume");
        if (null != mListener) {
            mHandler.post(() -> mListener.onPushResume());
        }
    }

    protected void notifyPushPause() {
        Log.d(TAG, "notifyPushPause");
        if (null != mListener) {
            mHandler.post(() -> mListener.onPushPause());
        }
    }

    protected void notifyPushStop() {
        Log.d(TAG, "notifyPushStop");
        if (null != mListener) {
            mHandler.post(() -> mListener.onPushStop());
        }
    }

    protected void notifyPushError(int errorCode, String error) {
        Log.d(TAG, "notifyPushError,errorCode:" + errorCode + "," + error);
        if (null != mListener) {
            mHandler.post(() -> mListener.onPushError(errorCode, error));
        }
    }

    protected void notifyPushPathSuccess(@Nullable String filename) {
        Log.d(TAG, "notifyPushPathSuccess,filename:" + filename);
        // 插入全景信息
        if (null != filename) {
            try {
                IMetadataCallback callback = PilotLib.self().getMetadataCallback();
                String appVersionName = "";
                String deviceName = "";
                if (callback != null) {
                    appVersionName = callback.getVersion();
                    deviceName = callback.getArtist();
                }
                PiPanoUtils.spatialMediaImpl(filename, mPushParam.isPanorama(), appVersionName, deviceName);
            } catch (Exception ignore) {
            }
        }
        if (null != mListener) {
            mHandler.post(() -> mListener.onPushPathSuccess(filename));
        }
    }
}
