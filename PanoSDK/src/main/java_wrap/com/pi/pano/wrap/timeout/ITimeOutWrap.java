package com.pi.pano.wrap.timeout;

/**
 * 超时辅助接口
 */
public interface ITimeOutWrap {

    /**
     * 开始超时计算
     */
    void start(long timeOutMs);

    /**
     * 取消超时
     */
    void cancel();

    /**
     * 恢复计时
     */
    void restart();

    /**
     * 停止释放
     */
    void stop();
}
