package com.pi.pano;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.pi.pano.ext.IAudioEncoderExt;
import com.pi.pano.ext.IAudioRecordExt;

import java.util.List;

public abstract class MediaRecorderUtilBase {
    /**
     * 音频采样率
     */
    public static final int AUDIO_SAMPLE_RATE = 48000;
    /**
     * 音频单次处理数据大小
     */
    public static final int AUDIO_BUFFER_SIZE = 4096;
    /**
     * 音频比特率，固定值
     */
    public static final int AUDIO_BITRATE = 128000;

    protected static Location sLocation;
    protected static final Object sLocationLock = new Object();

    protected String mFilename;
    protected PiCallback mCallback;
    protected Surface mSurface;

    /**
     * 设置gps信息,gps信息将用于写入视频
     */
    static void setLocationInfo(Location location) {
        synchronized (sLocationLock) {
            sLocation = location;
        }
    }

    public abstract void setAudioEncoderExt(List<IAudioEncoderExt> list);

    public abstract void setAudioRecordExt(IAudioRecordExt audioRecordExt);

    public void setCallback(PiCallback callback) {
        this.mCallback = callback;
    }

    public abstract Surface startRecord(Context context, @NonNull String filename,
                                        @NonNull String mime, int width, int height, int fps, int bitRate,
                                        int channelCount,
                                        boolean useForGoogleMap, int memomotionRatio, int previewFps);

    public abstract String stopRecord(boolean injectPanoMetadata, boolean isPano, String firmware, String artist, long firstRecordSensorTimestamp);

    public abstract Surface getSurface();

    protected void releaseSurface() {
        if (mSurface != null) {
            try {
                mSurface.release();
            } catch (Exception e) {
                Log.e("mediaRecord", "releaseSurface error :" + e.getMessage());
            }
            mSurface = null;
        }
    }
}
