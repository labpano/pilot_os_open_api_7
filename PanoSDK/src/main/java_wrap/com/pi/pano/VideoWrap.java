package com.pi.pano;

import android.util.Log;

import androidx.annotation.NonNull;

import com.pi.pano.error.PiError;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 录像
 */
public final class VideoWrap {
    private static final String TAG = VideoWrap.class.getSimpleName();
    /**
     * 开始时错误
     */
    public static final int ERROR_ON_START = -1001;
    /**
     * 停止错误
     */
    public static final int ERROR_ON_STOP = -1000;

    static final ExecutorService sExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "VideoRecord"));

    /**
     * 是否在录像中
     */
    public static boolean isInRecord() {
        return PilotSDK.isInRecord();
    }

    private static String sLastRecordPath;

    private static final AtomicBoolean stopBySelf = new AtomicBoolean(false);

    /**
     * 开始录像
     *
     * @param params   录像参数
     * @param listener 监听
     */
    public static void rawStartRecord(@NonNull VideoParams params, IVideoListener listener) {
        Log.d(TAG, "rawStartRecord :" + params);
        sExecutor.execute(() -> {
            sLastRecordPath = null;
            final AtomicBoolean hasError = new AtomicBoolean(false);
            try {
                PilotSDK.startVideo(params, new SimplePiCallback() {
                    @Override
                    public void onSuccess() {
                        super.onSuccess();
                        sLastRecordPath = params.getCurrentVideoFilename();
                        if (!stopBySelf.get() && hasError.get() && listener != null) {
                            listener.onRecordError(new PiError(VideoWrap.ERROR_ON_START, "startVideo error "), sLastRecordPath);
                        } else {
                            if (null != listener) {
                                listener.onRecordStart(params);
                            }
                        }
                    }

                    @Override
                    public void onError(PiError error) {
                        super.onError(error);
                        if (!stopBySelf.get()) {
                            if (listener != null) {
                                listener.onRecordError(error, sLastRecordPath);
                            }
                            PilotSDK.stopRecord(true, false);
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                hasError.set(true);
                if (listener != null) {
                    listener.onRecordError(new PiError(VideoWrap.ERROR_ON_START, "startVideo error :" + e.getMessage()),
                            sLastRecordPath);
                }
                PilotSDK.stopRecord(true, false);
            }
        });
    }

    /**
     * 停止录像。
     * 经测试 startRecord 和 stopRecord 之间应有一定间隔（建议大于1s），否则在反复快速开始、停止易出现卡住甚至死机情况出现。
     */
    public static void stopRecord(boolean stopBySplit, IVideoListener listener) {
        Log.d(TAG, "stopRecord start stopBySplit:" + stopBySplit + "," + stopBySelf.get() + "," + listener);
        stopBySelf.set(true);
        try {
            PilotSDK.stopRecord(true, stopBySplit, new PiCallback() {
                @Override
                public void onSuccess() {
                    stopBySelf.set(false);
                    if (null != listener) {
                        listener.onRecordStop(sLastRecordPath);
                    }
                }

                @Override
                public void onError(PiError error) {
                    Log.e(TAG, "stopRecord error ==> " + error);
                    stopBySelf.set(false);
                    if (listener != null) {
                        listener.onRecordError(new PiError(VideoWrap.ERROR_ON_STOP), sLastRecordPath);
                    }
                }
            }, sExecutor);
            Log.d(TAG, "stopRecord run stopBySplit:" + stopBySplit + "," + listener);
        } catch (Exception ex) {
            ex.printStackTrace();
            stopBySelf.set(false);
            if (listener != null) {
                listener.onRecordError(new PiError(VideoWrap.ERROR_ON_STOP), sLastRecordPath);
            }
        }
    }
}
