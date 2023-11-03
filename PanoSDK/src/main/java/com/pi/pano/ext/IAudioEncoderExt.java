package com.pi.pano.ext;

/**
 * 音频编码扩展的额外处理器
 */
public interface IAudioEncoderExt {
    /**
     * @param sampleRateInHz 采样率
     * @param channelCount   声道数量
     * @param audioFormat    位深
     */
    void configure(int sampleRateInHz, int channelCount, int audioFormat);

    /**
     * 编码处理
     *
     * @param inBuffer  输入的音频数据（pcm）
     * @param length    长度
     * @param outBuffer 处理后输出的音频数据
     * @return 处理后的长度
     */
    int encodeProcess(byte[] inBuffer, int length, byte[] outBuffer);

    void release();

    /**
     * 使用当前拓展功能
     *
     * @param used 是否使用当前拓展功能
     */
    void onUseExtChanged(boolean used);
}
