package com.pi.pano;

import android.content.Context;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;
import com.pi.pano.ext.IAudioEncoderExt;
import com.pi.pano.ext.IAudioRecordExt;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用MediaRecorder录制。
 * （无降噪处理）
 */
public class MediaRecorderUtil2 extends MediaRecorderUtilBase {
    private static final AtomicInteger sCount = new AtomicInteger(0);

    private final String TAG;

    private MediaRecorder mMediaRecorder;

    public MediaRecorderUtil2() {
        TAG = "MediaRecorder2-" + sCount.getAndIncrement();
    }

    @Override
    @Nullable
    public Surface startRecord(Context context, @NonNull String filename,
                               @NonNull String mime, int width, int height, int fps, int bitRate,
                               int channelCount,
                               boolean useForGoogleMap, int memomotionRatio, int previewFps) {
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
        final boolean needAudio = !useForGoogleMap && memomotionRatio == 0 && channelCount > 0;

        try {
            mMediaRecorder = new MediaRecorder();

            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            if (needAudio) {
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }

            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            if (needAudio) {
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mMediaRecorder.setAudioChannels(2);
                mMediaRecorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE);
                mMediaRecorder.setAudioEncodingBitRate(AUDIO_BITRATE);
            }
            mMediaRecorder.setVideoEncoder(convertVideoEncode(mime));
            mMediaRecorder.setVideoEncodingBitRate(bitRate);
            mMediaRecorder.setVideoFrameRate(fps);
            mMediaRecorder.setVideoSize(width, height);
            mMediaRecorder.setOnErrorListener((mr, what, extra) -> {
                Log.e(TAG, "error [" + what + "] [" + extra + "]");
                if (MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN == what &&
                        extra == -1007 && mCallback != null) {
                    new Thread(() -> {
                        if (mCallback != null) {
                            mCallback.onError(new PiError(PiErrorCode.RECORD_WRITE_FILE_ERROR));
                        }
                    }).start();
                }
            });
            mMediaRecorder.setOutputFile(filename);

            mMediaRecorder.prepare();

            mSurface = mMediaRecorder.getSurface();

            mMediaRecorder.start();

            return mSurface;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    @NonNull
    public String stopRecord(boolean injectPanoMetadata, boolean isPano, String firmware, String artist, long firstRecordSensorTimestamp) {
        Log.d(TAG, "stop record...");
        try {
            mMediaRecorder.stop();
            mMediaRecorder.release();
            mMediaRecorder = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        releaseSurface();
        if (injectPanoMetadata) {
            PiPano.spatialMediaImpl(mFilename, isPano, false, firmware, artist, 0);
        }
        if (firstRecordSensorTimestamp > 0) {
            PiPano.injectFirstFrameTimestamp(mFilename, firstRecordSensorTimestamp);
        }
        mCallback = null;
        return mFilename;
    }

    @Override
    public void setAudioEncoderExt(List<IAudioEncoderExt> list) {
    }

    @Override
    public void setAudioRecordExt(IAudioRecordExt audioRecordExt) {
    }

    @Override
    public Surface getSurface() {
        return mSurface;
    }

    private static int convertVideoEncode(String encode) {
        switch (encode) {
            case MediaFormat.MIMETYPE_VIDEO_HEVC:
                return MediaRecorder.VideoEncoder.HEVC;
            default:
            case MediaFormat.MIMETYPE_VIDEO_AVC:
                return MediaRecorder.VideoEncoder.H264;
        }
    }
}
