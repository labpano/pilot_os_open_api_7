package com.pi.pano;

import android.content.Context;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.pi.pano.ext.IAudioEncoderExt;
import com.pi.pano.ext.IAudioRecordExt;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MediaRecorderUtil3 extends MediaRecorderUtilBase {
    private static final AtomicInteger sCount = new AtomicInteger(0);
    private final String TAG;

    private long mNativeObj;

    private Surface mSurface;

    private native long nativeCreateNativeObj();

    private native void nativeRelease(long naviceObj);

    private native Surface nativeStartRecord(long naviceObj,
                                             String filename, String mime, int width, int height, int fps, int bitRate, int channelCount,
                                             boolean useForGoogleMap, int memomotionRatio, int previewFps);

    private native float nativeGetMemomotionTimeMultiple(long naviceObj);

    public MediaRecorderUtil3() {
        TAG = "MediaRecorder3-" + sCount.getAndIncrement();
        mNativeObj = nativeCreateNativeObj();
    }

    @Override
    public void setAudioEncoderExt(List<IAudioEncoderExt> list) {

    }

    @Override
    public void setAudioRecordExt(IAudioRecordExt audioRecordExt) {

    }

    @Override
    public Surface startRecord(Context context, @NonNull String filename, @NonNull String mime, int width, int height, int fps, int bitRate,
                               int channelCount, boolean useForGoogleMap, int memomotionRatio, int previewFps) {
        Log.i(TAG, "start record...,filename:" + filename + ",height:" + height +
                ",width:" + width + ",fps:" + fps + ",bitRate:" + bitRate +
                ",useForGoogleMap:" + useForGoogleMap + ",memomotionRatio:" + memomotionRatio +
                ",mime:" + mime + ",channelCount:" + channelCount + ",previewFps:" + previewFps);
        if (filename.toLowerCase().endsWith(".mp4")) {
            File f = new File(filename);
            if (f.exists()) {
                f.delete();
            }
        }
        mFilename = filename;

        mSurface = nativeStartRecord(mNativeObj, filename, mime, width, height, fps, bitRate, channelCount, useForGoogleMap, memomotionRatio, previewFps);

        if (mSurface == null) {
            nativeRelease(mNativeObj);
            mNativeObj = 0;
        }

        return mSurface;
    }

    @Override
    public String stopRecord(boolean injectPanoMetadata, boolean isPano, String firmware, String artist, long firstRecordSensorTimestamp) {
        float memomotionTimeMultiple = nativeGetMemomotionTimeMultiple(mNativeObj);
        nativeRelease(mNativeObj);
        mNativeObj = 0;
        releaseSurface();
        if (injectPanoMetadata) {
            PiPano.spatialMediaImpl(mFilename, isPano, false, firmware, artist, memomotionTimeMultiple);
        }
        return mFilename;
    }

    @Override
    public Surface getSurface() {
        return mSurface;
    }
}
