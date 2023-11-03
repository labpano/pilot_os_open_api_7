/*
 * Copyright (c) 2017. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */
package com.pi.pano;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;
import com.pi.pano.ext.IAudioEncoderExt;
import com.pi.pano.ext.IAudioRecordExt;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MediaRecorder工具类。
 * 使用MediaCodec录制。
 */
public class MediaRecorderUtil extends MediaRecorderUtilBase {

    private static final AtomicInteger sCount = new AtomicInteger(0);
    private final String TAG;

    /**
     * 音频额外编码处理器。
     * 如：音频降噪。
     */
    private List<IAudioEncoderExt> mAudioEncoderExtList;
    private IAudioRecordExt mAudioRecordExt;

    private MediaMuxer mMediaMuxer;
    private VideoThread mVideoThread;
    private AudioThread mAudioThread;

    private volatile boolean mRun;

    public MediaRecorderUtil() {
        TAG = "MediaRecorder-" + sCount.getAndIncrement();
    }

    /**
     * 设置音频额外编码处理器,
     * 应在开始录像前设置。
     */
    @Override
    public void setAudioEncoderExt(List<IAudioEncoderExt> list) {
        mAudioEncoderExtList = list;
    }

    @Override
    public void setAudioRecordExt(IAudioRecordExt audioRecordExt) {
        mAudioRecordExt = audioRecordExt;
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
        try {
            mMediaMuxer = new MediaMuxer(filename, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        mRun = true;
        try {
            mVideoThread = new VideoThread(context, useForGoogleMap, memomotionRatio);
            final boolean needAudio = !useForGoogleMap && memomotionRatio == 0 && channelCount > 0;
            if (needAudio) {
                if (null == mAudioRecordExt) {
                    mAudioRecordExt = new DefaultAudioRecordExt();
                }
                mAudioThread = new AudioThread(mAudioRecordExt, mAudioEncoderExtList);
                mAudioThread.prepare(AUDIO_SAMPLE_RATE, channelCount, AUDIO_BITRATE);
            }
            Surface surface = mVideoThread.prepare(mime, width, height, fps, bitRate, previewFps);
            mVideoThread.start();
            if (needAudio) {
                mAudioThread.start();
            }
            return surface;
        } catch (Exception ex) {
            Log.e(TAG, "start record error:" + ex);
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    @NonNull
    public String stopRecord(boolean injectPanoMetadata, boolean isPano, String firmware, String artist, long firstRecordSensorTimestamp) {
        Log.d(TAG, "stop record...");
        mRun = false;
        long firstVideoFrameTimestamp = mVideoThread.getFirstFrameTimestamp();
        try {
            mVideoThread.join();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        releaseSurface();
        if (mAudioThread != null) {
            try {
                mAudioThread.join();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (mMediaMuxer != null) {
            try {
                mMediaMuxer.stop();
                mMediaMuxer.release();
                Log.d(TAG, "media muxer stop.");
            } catch (Exception ex) {
                Log.e(TAG, "media muxer stop error:" + ex);
                ex.printStackTrace();
            }
        }
        if (injectPanoMetadata) {
            PiPano.spatialMediaImpl(mFilename, isPano, false, firmware, artist, mVideoThread.getMemomotionTimeMultiple());
        }
        if (firstVideoFrameTimestamp > 0) {
            PiPano.injectFirstFrameTimestamp(mFilename, firstVideoFrameTimestamp * 1000);
        }
        Log.d(TAG, "stop record end.");
        mCallback = null;
        return mFilename;
    }

    @NonNull
    private static MediaCodec createVideoEncoder(@NonNull String mime, int width, int height,
                                                 int fps, int bitrate,
                                                 boolean useForGoogleMap, long memomotionRatio,
                                                 int previewFps) throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        setVideoFormatParams(format, width, height, fps, bitrate, useForGoogleMap, memomotionRatio, previewFps);
        MediaCodec codec = MediaCodec.createEncoderByType(mime);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return codec;
    }

    private static MediaCodec createAudioEncoder(int sampleRate, int channelCount, int bitrate) throws IOException {
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return codec;
    }

    private void waitAddAudioTrackAndMuxerStart() {
        if (mAudioThread != null) {
            int waitTime = 30;
            while (mAudioThread.mAudioEncoderTrack == -1 && waitTime > 0) {
                Log.d(TAG, "video wait for audio track to be added.");
                SystemClock.sleep(100);
                waitTime--;
            }
        }
        mMediaMuxer.start();
        Log.d(TAG, "media muxer start...");
    }

    private void requestAddAudioFrame(long firstFrameTimestamp) {
        if (mAudioThread != null) {
            mAudioThread.requestAddAudioFrame(firstFrameTimestamp);
        }
    }

    @Override
    public Surface getSurface() {
        return mVideoThread == null ? null : mSurface;
    }

    private class VideoThread extends Thread {
        private final Context context;
        private final boolean useForGoogleMap;
        private final long memomotionRatio;

        /**
         * 编码时间放大倍数，重新计算帧时间戳使用。
         */
        private float mMemomotionTimeMultiple = -1;

        private MediaCodec mVideoEncoder;
        private int mVideoEncoderTrack = -1;
        private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

        private int mCammEncoderTrack = -1;
        private final MediaCodec.BufferInfo mCammBufferInfo = new MediaCodec.BufferInfo();
        private IMUDataCollector mIMUDataCollector;
        private final ByteBuffer mCammBuffer = ByteBuffer.allocate(64);

        /**
         * 第一帧时间戳
         */
        private volatile long mFirstFrameTimestamp = -1;

        float getMemomotionTimeMultiple() {
            if (mMemomotionTimeMultiple > 0) {
                return mMemomotionTimeMultiple;
            }
            return 1;
        }

        VideoThread(Context context, boolean useForGoogleMap, long memomotionRatio) {
            super("VideoThread-" + sCount.get());
            this.context = context;
            this.useForGoogleMap = useForGoogleMap;
            this.memomotionRatio = memomotionRatio;
        }

        long getFirstFrameTimestamp() {
            return mFirstFrameTimestamp;
        }

        Surface prepare(@NonNull String mime, int width, int height, int fps, int bitRate, int previewFps) throws IOException {
            try {
                // 计算放大倍数，延时摄影、慢动作按30fps编码，需修改时间戳；街景视频无需改变时间戳
                if (!useForGoogleMap && memomotionRatio != 0) {
                    float deltaTimestamp = 1.0f / previewFps * Math.abs(memomotionRatio);
                    mMemomotionTimeMultiple = (1.0f / 30.0f) / deltaTimestamp;
                    Log.d(TAG, "video memomotiontime multiple:" + mMemomotionTimeMultiple);
                }
                if (useForGoogleMap) {
                    addCommTrack();
                    mIMUDataCollector = new IMUDataCollector(context);
                    mIMUDataCollector.register();
                }
                mVideoEncoder = createVideoEncoder(mime, width, height, fps, bitRate, useForGoogleMap, memomotionRatio, previewFps);
                Surface surface = mVideoEncoder.createInputSurface();
                mVideoEncoder.start();
                mSurface = surface;
                return surface;
            } catch (Exception ex) {
                release();
                throw ex;
            }
        }

        @Override
        public void run() {
            Log.d(TAG, "video encoder thread start...");
            try {
                while (mRun) {
                    encodeVideoFrame();
                }
            } catch (IllegalStateException e) {
                e.printStackTrace();
                String message = e.getMessage();
                Log.e(TAG, "encodeVideoFrame error ：" + message);
                if (message != null && message.contains("writeSampleData returned an error") && mCallback != null) {
                    new Thread(() -> {
                        if (mCallback != null) {
                            mCallback.onError(new PiError(PiErrorCode.RECORD_WRITE_FILE_ERROR, message));
                        }
                    }).start();
                }
            }
            release();
            Log.d(TAG, "video encoder thread exit.");
        }

        private void release() {
            if (null != mVideoEncoder) {
                try {
                    mVideoEncoder.stop();
                    mVideoEncoder.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mVideoEncoder = null;
            }
            if (null != mIMUDataCollector) {
                mIMUDataCollector.unregister();
                mIMUDataCollector = null;
            }
        }

        private void encodeVideoFrame() {
            int outputIndex = mVideoEncoder.dequeueOutputBuffer(mBufferInfo, 100_000);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mVideoEncoder.getOutputFormat();
                mVideoEncoderTrack = mMediaMuxer.addTrack(format);
                Log.d(TAG, "video track added.");

                waitAddAudioTrackAndMuxerStart();

                outputIndex = mVideoEncoder.dequeueOutputBuffer(mBufferInfo, 0);
            }

            if (outputIndex >= 0) {
                ByteBuffer buffer = mVideoEncoder.getOutputBuffer(outputIndex);
                if (buffer != null && mBufferInfo.size > 0) {
                    if (mBufferInfo.presentationTimeUs > 0) {
                        if (mFirstFrameTimestamp > -1) { // 非首帧
                            if (mMemomotionTimeMultiple > 0) {
                                // 重新计算时间戳
                                long deltaTime = mBufferInfo.presentationTimeUs - mFirstFrameTimestamp;
                                mBufferInfo.presentationTimeUs = mFirstFrameTimestamp + (long) (deltaTime * mMemomotionTimeMultiple);
                            }
                        }
                        mMediaMuxer.writeSampleData(mVideoEncoderTrack, buffer, mBufferInfo);

                        if (mFirstFrameTimestamp < 0) {
                            mFirstFrameTimestamp = mBufferInfo.presentationTimeUs;
                            Log.d(TAG, "video first frame timestamp:" + mFirstFrameTimestamp);

                            // 音频需等视频一帧编码后再加入
                            requestAddAudioFrame(mFirstFrameTimestamp);
                        }

                        if (mCammEncoderTrack >= 0) {
                            encodeCammFrame();
                        }
                    } else {
                        Log.e(TAG, "video sample time must > 0.");
                    }
                }
                mVideoEncoder.releaseOutputBuffer(outputIndex, false);
            }
        }

        /**
         * 编入一帧camm数据
         */
        private void encodeCammFrame() {
            //GPS信息
            synchronized (sLocationLock) {
                if (sLocation != null) {
                    writeGpsData(sLocation);
                }
                sLocation = null;
            }
            final long cameraTimestamp = (SystemClock.elapsedRealtime() - 420) * 1000000;
            //加速度
            SimpleSensorEvent accelerometerEvent = mIMUDataCollector.findAccelerometerEvent(cameraTimestamp);
            if (accelerometerEvent != null) {
                writeAccelerometerData(3, accelerometerEvent);
            }
            //陀螺仪
            SimpleSensorEvent gyroscopeEvent = mIMUDataCollector.findGyroscopeEvent(cameraTimestamp);
            if (null != gyroscopeEvent) {
                writeAccelerometerData(2, gyroscopeEvent);
            }
        }

        private final byte[] mBitToLittleShortBuffer = new byte[2];
        private final byte[] mBitToLittleIntBuffer = new byte[4];
        private final byte[] mBitToLittleFloatBuffer = new byte[4];
        private final byte[] mBitToLittleDoubleBuffer = new byte[8];

        // 最后的有效访方位信息
        private float mLastBearing = -1;

        private void writeGpsData(@NonNull Location location) {
            mCammBuffer.clear();
            mCammBuffer.putShort((short) 0);
            mCammBuffer.put(BitToLittleShort((short) 6, mBitToLittleShortBuffer));
            //计算gps时间
            double timeGpsEpoch =
                    (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000.0 //系统启动时间UTC
                            + location.getElapsedRealtimeNanos() / 1000000000.0   //上次定位的时间
                            - 315964800 //UTC时间和GPS时间的转换
                            + 18;   //UTC时间和GPS时间的转换
            mCammBuffer.put(BitToLittleDouble(timeGpsEpoch, mBitToLittleDoubleBuffer));
            mCammBuffer.put(BitToLittleInt(3, mBitToLittleIntBuffer));
            mCammBuffer.put(BitToLittleDouble(location.getLatitude(), mBitToLittleDoubleBuffer));
            mCammBuffer.put(BitToLittleDouble(location.getLongitude(), mBitToLittleDoubleBuffer));
            mCammBuffer.put(BitToLittleFloat((float) location.getAltitude(), mBitToLittleFloatBuffer));
            mCammBuffer.put(BitToLittleFloat(location.getAccuracy(), mBitToLittleFloatBuffer));
            mCammBuffer.put(BitToLittleFloat(location.getAccuracy(), mBitToLittleFloatBuffer));//mLocation.getVerticalAccuracyMeters() 需要level26
            float speed = location.getSpeed();
            //上一次记录的location方位信息,如果这一次location不包含方位信息,就用上一次的
            if (location.hasBearing()) {
                // 更新方位信息
                mLastBearing = location.getBearing() / 180.0f * 3.1415927f;
            }
            mCammBuffer.put(BitToLittleFloat(mLastBearing >= 0 ? speed * (float) Math.sin(mLastBearing) : 0, mBitToLittleFloatBuffer));  //velocity_east
            mCammBuffer.put(BitToLittleFloat(mLastBearing >= 0 ? speed * (float) Math.cos(mLastBearing) : 0, mBitToLittleFloatBuffer)); //velocity_north
            mCammBuffer.put(BitToLittleFloat(0, mBitToLittleFloatBuffer)); //velocity_up
            mCammBuffer.put(BitToLittleFloat(0, mBitToLittleFloatBuffer));//mLocation.getSpeedAccuracyMetersPerSecond() 需要level26
            mCammBufferInfo.set(0, mCammBuffer.position(), mBufferInfo.presentationTimeUs, 0);
            mMediaMuxer.writeSampleData(mCammEncoderTrack, mCammBuffer, mCammBufferInfo);
        }

        private void writeAccelerometerData(int type, @NonNull SimpleSensorEvent accelerometerEvent) {
            mCammBuffer.clear();
            mCammBuffer.putShort((short) 0);
            mCammBuffer.put(BitToLittleShort((short) type, mBitToLittleShortBuffer));
            mCammBuffer.put(BitToLittleFloat(accelerometerEvent.x, mBitToLittleFloatBuffer));
            mCammBuffer.put(BitToLittleFloat(-accelerometerEvent.z, mBitToLittleFloatBuffer));
            mCammBuffer.put(BitToLittleFloat(accelerometerEvent.y, mBitToLittleFloatBuffer));
            mCammBufferInfo.set(0, mCammBuffer.position(), mBufferInfo.presentationTimeUs, 0);
            mMediaMuxer.writeSampleData(mCammEncoderTrack, mCammBuffer, mCammBufferInfo);
        }

        /**
         * 添加 camm 轨道
         */
        private void addCommTrack() {
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "application/camm");
            mCammEncoderTrack = mMediaMuxer.addTrack(format);
        }
    }

    private static class SimpleSensorEvent {
        long timestamp;
        float x, y, z;

        SimpleSensorEvent(long t, float x, float y, float z) {
            timestamp = t;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * imu 数据采集（加速度、陀螺仪）
     */
    private final static class IMUDataCollector {
        private final SensorManager mSensorManager;
        private final ConcurrentLinkedQueue<SimpleSensorEvent> mAccelerometerEventQueue = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<SimpleSensorEvent> mGyroscopeEventQueue = new ConcurrentLinkedQueue<>();

        private final SensorEventListener mSensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                switch (event.sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        mAccelerometerEventQueue.offer(new SimpleSensorEvent(
                                event.timestamp, event.values[0], event.values[1], event.values[2]));
                        break;
                    case Sensor.TYPE_GYROSCOPE:
                        mGyroscopeEventQueue.offer(new SimpleSensorEvent(
                                event.timestamp, event.values[0], event.values[1], event.values[2]));
                        break;
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        public IMUDataCollector(Context context) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        }

        void register() {
            mSensorManager.registerListener(mSensorEventListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
            mSensorManager.registerListener(mSensorEventListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
        }

        void unregister() {
            mSensorManager.unregisterListener(mSensorEventListener);
        }

        @Nullable
        private SimpleSensorEvent findAccelerometerEvent(long cameraTimestamp) {
            return getSensorEventByTime(mAccelerometerEventQueue, cameraTimestamp);
        }

        @Nullable
        private SimpleSensorEvent findGyroscopeEvent(long cameraTimestamp) {
            return getSensorEventByTime(mGyroscopeEventQueue, cameraTimestamp);
        }

        @Nullable
        private SimpleSensorEvent getSensorEventByTime(Queue<SimpleSensorEvent> queue, long timeStamp) {
            SimpleSensorEvent lastEvent = queue.poll();
            if (lastEvent == null) {
                return null;
            }
            while (true) {
                SimpleSensorEvent event = queue.poll();
                if (event == null) {
                    return lastEvent;
                }
                if (lastEvent.timestamp >= timeStamp) {
                    return lastEvent;
                } else if (event.timestamp > timeStamp) {
                    return Math.abs(lastEvent.timestamp - timeStamp) <
                            Math.abs(event.timestamp - timeStamp) ? lastEvent : event;
                }
                lastEvent = event;
            }
        }
    }

    private class AudioThread extends Thread {
        private IAudioRecordExt audioRecordExt;
        private List<IAudioEncoderExt> audioEncoderExtList;

        private byte[] mAudioRecordBuffer;
        private byte[] mAudioEncoderBufferExt;

        private volatile int mAudioEncoderTrack = -1;
        private MediaCodec mAudioEncoder;
        private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

        /**
         * 第一帧时间戳
         */
        private volatile long mFirstFrameTimestamp = -1;
        /**
         * 最后一帧的时间戳，默认最大（不编码音频—），
         * 在视频第一帧编码后同步赋值时间，并使音频开始编码写入数据。
         */
        private volatile long mLastPresentationTimeUs = Long.MAX_VALUE;

        AudioThread(@NonNull IAudioRecordExt audioRecordExt, @Nullable List<IAudioEncoderExt> audioEncoderExtList) {
            super("AudioThread-" + sCount.get());
            this.audioEncoderExtList = audioEncoderExtList;
            this.audioRecordExt = audioRecordExt;
        }

        void prepare(int sampleRate, int channelCount, int bitrate) throws IOException {
            try {
                final int channelConfig = (channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
                final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
                final int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding);

                mAudioRecordBuffer = new byte[AUDIO_BUFFER_SIZE];
                audioRecordExt.configure(MediaRecorder.AudioSource.MIC,
                        sampleRate, channelConfig, audioEncoding, Math.max(minBufferSize, mAudioRecordBuffer.length));

                if (audioEncoderExtList != null) {
                    mAudioEncoderBufferExt = new byte[mAudioRecordBuffer.length];
                    for (IAudioEncoderExt encoderExt : audioEncoderExtList) {
                        encoderExt.configure(sampleRate, channelCount, audioEncoding);
                    }
                }

                mAudioEncoder = createAudioEncoder(sampleRate, channelCount, bitrate);
                audioRecordExt.startRecording();
                mAudioEncoder.start();
            } catch (Exception ex) {
                release();
                throw ex;
            }
        }

        void requestAddAudioFrame(long firstFrameTimestamp) {
            mLastPresentationTimeUs = firstFrameTimestamp;
        }

        @Override
        public void run() {
            Log.d(TAG, "audio encoder thread start...");
            while (mRun) {
                encodeAudioFrame();
            }

            release();

            Log.d(TAG, "audio encoder thread exit.");
        }

        private void release() {
            if (audioRecordExt != null) {
                try {
                    audioRecordExt.stop();
                    audioRecordExt.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                audioRecordExt = null;
            }
            if (null != mAudioEncoder) {
                try {
                    mAudioEncoder.stop();
                    mAudioEncoder.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mAudioEncoder = null;
            }
            if (audioEncoderExtList != null) {
                for (IAudioEncoderExt ext : audioEncoderExtList) {
                    try {
                        ext.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                audioEncoderExtList = null;
            }
        }

        private void encodeAudioFrame() {
            int inputIndex = mAudioEncoder.dequeueInputBuffer(0);
            if (inputIndex >= 0) {
                final long pts = System.nanoTime() / 1000;
                // 读取音频数据
                int readCount = audioRecordExt.read(mAudioRecordBuffer, 0, mAudioRecordBuffer.length); // read audio raw data
                if (readCount < 0) {
                    return;
                }
                ByteBuffer inputBuffer = mAudioEncoder.getInputBuffer(inputIndex);
                if (audioEncoderExtList != null) {
                    // 使用外部编码器预先对数据处理，如降噪sdk、全景音sdk
                    byte[] in = mAudioRecordBuffer;
                    byte[] out = mAudioEncoderBufferExt;
                    byte[] dst = in; // 处理的后结果引用
                    for (IAudioEncoderExt encoderExt : audioEncoderExtList) {
                        int count = encoderExt.encodeProcess(in, readCount, out);
                        if (count != -1) { // 成功处理，out有效
                            readCount = count;
                            dst = out;
                            // 交换输入、输出指向，进行下轮处理
                            out = in;
                            in = dst;
                        }
                    }
                    inputBuffer.put(dst, 0, readCount);
                } else {
                    inputBuffer.put(mAudioRecordBuffer);
                }
                mAudioEncoder.queueInputBuffer(inputIndex, 0, readCount, pts, 0);
            }

            int outputIndex = mAudioEncoder.dequeueOutputBuffer(mBufferInfo, 100_000);
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mAudioEncoder.getOutputFormat();
                mAudioEncoderTrack = mMediaMuxer.addTrack(format);
                Log.d(TAG, "audio track added.");

                outputIndex = mAudioEncoder.dequeueOutputBuffer(mBufferInfo, 0);
            }

            if (outputIndex >= 0) {
                ByteBuffer buffer = mAudioEncoder.getOutputBuffer(outputIndex);
                if (buffer != null) {
                    if (mBufferInfo.presentationTimeUs > 0) {
                        if (mBufferInfo.presentationTimeUs >= mLastPresentationTimeUs) {
                            mMediaMuxer.writeSampleData(mAudioEncoderTrack, buffer, mBufferInfo);
                            mLastPresentationTimeUs = mBufferInfo.presentationTimeUs;

                            if (mFirstFrameTimestamp < 0) {
                                mFirstFrameTimestamp = mBufferInfo.presentationTimeUs;
                                Log.d(TAG, "audio first frame timestamp:" + mFirstFrameTimestamp);
                            }
                        } else {
                            // 等候视频帧，或音频时间非递增，忽略此数据
                            Log.d(TAG, "audio wait for video first frame to be added or time is not incremental,throw data.");
                        }
                    } else {
                        Log.e(TAG, "audio sample time must > 0.");
                    }
                }
                mAudioEncoder.releaseOutputBuffer(outputIndex, false);
            }
        }
    }

    /**
     * 配置编码参数
     */
    private static void setVideoFormatParams(MediaFormat videoFormat,
                                             int width, int height, int fps, int bitrate,
                                             boolean useForGoogleMap, long memomotionRatio,
                                             int previewFps) {
        setVideoFormatParams(videoFormat, width, height, fps, bitrate);
        if (memomotionRatio > 0 && !useForGoogleMap) {
            videoFormat.setInteger("vendor.qti-ext-enc-qp-range.qp-i-min", 20);
            videoFormat.setInteger("vendor.qti-ext-enc-qp-range.qp-b-min", 20);
            videoFormat.setInteger("vendor.qti-ext-enc-qp-range.qp-p-min", 20);
            videoFormat.setInteger("vendor.qti-ext-enc-intra-period.n-pframes", 2);
        } else if (memomotionRatio < 0) {
            // 慢动作时，使用预览帧率
            videoFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, previewFps);
        }
    }

    /**
     * 配置编码参数
     */
    private static void setVideoFormatParams(MediaFormat videoFormat,
                                             int width, int height, int fps, int bitrate) {
        //视频编码器
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
//        videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        //videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        //rc mode 0-RC Off 1-VBR_CFR 2-CBR_CFR 3-VBR_VFR 4-CBR_VFR 5-MBR_CFR 6_MBR_VFR
        videoFormat.setInteger("vendor.qti-ext-enc-bitrate-mode.value", 1);
        // Real Time Priority
        videoFormat.setInteger(MediaFormat.KEY_PRIORITY, 0);
        // Enable hier-p for resolution greater than 5.7k
        if (width >= 5760 && height >= 2880) {
            videoFormat.setString(MediaFormat.KEY_TEMPORAL_LAYERING, "android.generic.2");
        }
        videoFormat.setInteger("vendor.qti-ext-enc-qp-range.qp-i-min", 10);
        videoFormat.setInteger("vendor.qti-ext-enc-qp-range.qp-i-max", 51);
        videoFormat.setInteger("vendor.qti-ext-enc-qp-range.qp-b-min", 10);
        videoFormat.setInteger("vendor.qti-ext-enc-qp-range.qp-b-max", 51);
        videoFormat.setInteger("vendor.qti-ext-enc-qp-range.qp-p-min", 10);
        videoFormat.setInteger("vendor.qti-ext-enc-qp-range.qp-p-max", 51);
        videoFormat.setInteger("vendor.qti-ext-enc-initial-qp.qp-i-enable", 1);
        videoFormat.setInteger("vendor.qti-ext-enc-initial-qp.qp-i", 10);
        videoFormat.setInteger("vendor.qti-ext-enc-initial-qp.qp-b-enable", 1);
        videoFormat.setInteger("vendor.qti-ext-enc-initial-qp.qp-b", 10);
        videoFormat.setInteger("vendor.qti-ext-enc-initial-qp.qp-p-enable", 1);
        videoFormat.setInteger("vendor.qti-ext-enc-initial-qp.qp-p", 10);

        int interval_iframe = 1;
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, interval_iframe);
        // Calculate P frames based on FPS and I frame Interval
        int pFrameCount = (fps * interval_iframe) - 1;
        if (pFrameCount < 1) {
            pFrameCount = 1; // p帧不能小于1
        }
        videoFormat.setInteger("vendor.qti-ext-enc-intra-period.n-pframes", pFrameCount);
        // Always set B frames to 0.
        videoFormat.setInteger("vendor.qti-ext-enc-intra-period.n-bframes", 0);
        videoFormat.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
    }


    private static byte[] BitToLittleShort(short s, byte[] bytes) {
        bytes[1] = (byte) (s >> 8);
        bytes[0] = (byte) (s);
        return bytes;
    }

    private static byte[] BitToLittleInt(int i, byte[] bytes) {
        bytes[3] = (byte) (i >> 24);
        bytes[2] = (byte) (i >> 16);
        bytes[1] = (byte) (i >> 8);
        bytes[0] = (byte) (i);
        return bytes;
    }

    private static byte[] BitToLittleFloat(float f, byte[] bytes) {
        int n = Float.floatToIntBits(f);
        bytes[3] = (byte) (n >> 24);
        bytes[2] = (byte) (n >> 16);
        bytes[1] = (byte) (n >> 8);
        bytes[0] = (byte) (n);
        return bytes;
    }

    private static byte[] BitToLittleDouble(double f, byte[] bytes) {
        long n = Double.doubleToLongBits(f);
        bytes[7] = (byte) (n >> 56);
        bytes[6] = (byte) (n >> 48);
        bytes[5] = (byte) (n >> 40);
        bytes[4] = (byte) (n >> 32);
        bytes[3] = (byte) (n >> 24);
        bytes[2] = (byte) (n >> 16);
        bytes[1] = (byte) (n >> 8);
        bytes[0] = (byte) (n);
        return bytes;
    }
}
