package com.pi.pano;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pi.pano.annotation.PiPhotoFileFormat;
import com.pi.pano.annotation.PiResolution;
import com.pi.pano.wrap.IPhotoListener;
import com.pi.pano.wrap.TimeOutTakePhotoListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 拍照
 */
public final class PhotoWrap {
    private final static String TAG = PhotoWrap.class.getSimpleName();

    public static final String sPhotoMaxResolution = PiResolution._5_7K;

    static final ExecutorService sExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Photo-" + mCount.incrementAndGet());
        }
    });

    /**
     * 拍照
     */
    public static void takePhoto(PhotoParams params, IPhotoListener listener) {
        rawTakePhoto(params, listener);
    }

    private static void rawTakePhoto(PhotoParams params, IPhotoListener listener) {
        Log.d(TAG, "take photo:" + params);
        sExecutor.execute(() -> {
            TimeOutTakePhotoListener takePhotoListener = new TimeOutTakePhotoListener(listener, new Handler(Looper.getMainLooper()));
            takePhotoListener.mParams = params;
            if (PiPhotoFileFormat.jpg_raw.equals(params.fileFormat) ||
                    PiPhotoFileFormat.jpg_dng.equals(params.fileFormat)) {
                takePhotoListener.setTotalTakeCount(2);
            }
            takePhotoListener.notifyTakeStart();
            PilotSDK.takePhoto(takePhotoListener);
        });
    }
}
