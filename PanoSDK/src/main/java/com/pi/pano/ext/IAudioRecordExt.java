package com.pi.pano.ext;

import android.media.AudioFormat;
import android.media.MediaRecorder;

/**
 * 录音接口
 */
public interface IAudioRecordExt {

    /**
     * 初始化 创建 record 对象
     *
     * @param audioSource       录音源 见 {@link MediaRecorder.AudioSource }
     * @param sampleRateInHz    采样率
     * @param channelConfig     通道数配置
     *                          单声道：{@link AudioFormat#CHANNEL_IN_MONO},
     *                          双声道：{@link AudioFormat#CHANNEL_IN_STEREO} ，
     *                          4声道：{@link AudioFormat#CHANNEL_IN_STEREO}|{@link AudioFormat#CHANNEL_IN_FRONT}|{{@link AudioFormat#CHANNEL_IN_BACK}}
     * @param audioFormat       录音数据格式
     *                          {@link AudioFormat#ENCODING_PCM_16BIT},
     *                          {@link AudioFormat#ENCODING_PCM_FLOAT}.
     * @param bufferSizeInBytes 录音缓冲区大小
     */
    void configure(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes);

    /**
     * 开始录制
     */
    void startRecording();

    /**
     * 停止录制
     */
    void stop();

    /**
     * 释放
     */
    void release();

    /**
     * 同步读取录音数据
     *
     * @param pcmData       用于接收返回的pcm数据
     * @param offsetInBytes 开始下标
     * @param sizeInBytes   读取的长度
     * @return <0 失败 or sizeInBytes
     */
    int read(byte[] pcmData, int offsetInBytes, int sizeInBytes);

    /**
     * 同步读取录音数据 （ format == AudioFormat#ENCODING_PCM_FLOAT 使用）
     *
     * @param pcmData       用于接收返回的pcm数据
     * @param offsetInBytes 开始下标
     * @param sizeInBytes   读取的长度
     * @return <0 失败 or sizeInBytes
     */
    int read(float[] pcmData, int offsetInBytes, int sizeInBytes);
}
