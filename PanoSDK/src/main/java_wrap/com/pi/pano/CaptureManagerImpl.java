package com.pi.pano;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiPhotoFileFormat;
import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;
import com.pi.pano.wrap.IMetadataCallback;
import com.pi.pano.wrap.IPhotoListener;
import com.pi.pano.wrap.PhotoErrorCode;
import com.pi.pano.wrap.TimeOutTakePhotoListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaptureManagerImpl implements ICaptureManager {

    private static final String TAG = "captureManager";

    /**
     * 开始时错误
     */
    public static final int ERROR_ON_START = -1001;
    /**
     * 停止错误
     */
    public static final int ERROR_ON_STOP = -1000;

    private static final ExecutorService sExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "th-capture"));

    private final Handler mHandler;

    public CaptureManagerImpl() {
        HandlerThread thread = new HandlerThread("takeScreenPhoto");
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }

    private static String sLastRecordPath;
    private static final AtomicBoolean stopBySelf = new AtomicBoolean(false);

    @Override
    public void takePhoto(@NonNull PhotoParams params, @NonNull IPhotoListener listener) {
        Log.d(TAG, "takePhoto :" + params);
        sExecutor.execute(() -> {
            try {
                IMetadataCallback metadataCallback = PilotLib.self().getMetadataCallback();
                if (metadataCallback != null) {
                    params.artist = metadataCallback.getArtist();
                    params.software = metadataCallback.getVersion();
                }
                TimeOutTakePhotoListener takePhotoListener = new TimeOutTakePhotoListener(listener);
                takePhotoListener.mParams = params;
                if (PiPhotoFileFormat.jpg_raw.equals(params.fileFormat) ||
                        PiPhotoFileFormat.jpg_dng.equals(params.fileFormat)) {
                    takePhotoListener.setTotalTakeCount(2);
                }
                takePhotoListener.notifyTakeStart();
                PilotSDK.takePhoto(takePhotoListener);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "takePhoto error :" + e.getMessage() + " ==> " + params);
                listener.onTakeError(params.isHdr(),
                        PhotoErrorCode.error + "(" + PiErrorCode.UN_KNOWN + "," + e.getMessage() + ")");
            }
        });
    }

    @Override
    public void takeScreenPhoto(String filepath, int width, int height, @NonNull TakePhotoListener3 listener3) {
        Log.d(TAG, "takeScreenPhoto :" + width + "*" + height + ", " + filepath);
        mHandler.post(() -> {
            try {
                PilotSDK.pickPhoto(filepath, width, height, listener3);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "takeScreenPhoto error :" + e.getMessage() + ", ==>" + filepath);
                listener3.onTakePhotoError(PiErrorCode.UN_KNOWN);
            }
        });
    }

    @Override
    public void startRecord(@NonNull VideoParams params, IVideoListener listener) {
        Log.d(TAG, "startRecord :" + params);
        sExecutor.execute(() -> {
            sLastRecordPath = null;
            final AtomicBoolean hasError = new AtomicBoolean(false);
            try {
                IMetadataCallback metadataCallback = PilotLib.self().getMetadataCallback();
                if (metadataCallback != null) {
                    params.mArtist = metadataCallback.getArtist();
                    params.mVersionName = metadataCallback.getVersion();
                }
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

    @Override
    public void stopRecord(boolean continueRecord, IVideoListener listener) {
        Log.d(TAG, "stopRecord start continueRecord:" + continueRecord + "," + stopBySelf.get() + "," + listener);
        stopBySelf.set(true);
        try {
            PilotSDK.stopRecord(true, continueRecord, new PiCallback() {
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
            Log.d(TAG, "stopRecord run continueRecord:" + continueRecord + "," + listener);
        } catch (Exception ex) {
            ex.printStackTrace();
            stopBySelf.set(false);
            if (listener != null) {
                listener.onRecordError(new PiError(VideoWrap.ERROR_ON_STOP), sLastRecordPath);
            }
        }
    }

}
