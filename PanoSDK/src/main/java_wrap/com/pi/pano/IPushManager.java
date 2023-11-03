package com.pi.pano;

import android.util.Size;

import androidx.annotation.IntDef;

import com.pi.pano.annotation.PiVideoEncode;

/**
 * 直播推流管理接口
 */
public interface IPushManager {
    /**
     * 准备直播
     *
     * @param params       直播参数接口
     * @param pushListener 回调接口
     */
    void prepare(IParams params, IPushListener pushListener);

    /**
     * 开始直播
     */
    void startPush();

    /**
     * 尝试重新连接直播
     */
    void restartPush();

    /**
     * 恢复直播 , 在{@link #pausePush()}后调用
     */
    void resumePush();

    /**
     * 暂停直播 ，暂停后需要{@link #resumePush()}恢复
     */
    void pausePush();

    /**
     * 停止直播
     */
    void stopPush();

    /**
     * 更新 保存直播录像的 地址
     *
     * @param filePath 文件路径
     */
    void updateRecordPath(String filePath);

    /**
     * 实时推流码流
     *
     * @return bps
     */
    long getRealBitrate();

    /**
     * 直播网络质量
     *
     * @return 0:普通 ，1：好 ，-1：差
     */
    @PushNetworkQuality
    int getNetworkQuality();


    interface IPushListener {
        /**
         * 推流开始
         */
        void onPushStart();

        /**
         * 推流准备中
         */
        void onPushPrepare();

        /**
         * 推流恢复
         */
        void onPushResume();

        /**
         * 推流暂停
         */
        void onPushPause();

        /**
         * 推流停止
         */
        void onPushStop();

        /**
         * 推流错误
         *
         * @param errorCode 对应类型的错误码
         * @param error     错误信息
         */
        void onPushError(int errorCode, String error);

        /**
         * 录制的每个分段视频
         */
        void onPushPathSuccess(String filePath);

    }

    interface IParams {
        /**
         * 推流地址
         *
         * @return url
         */
        String getPushUrl();

        /**
         * 直播协议
         *
         * @return {@link IProtocol}
         */
        @IProtocol
        int getProtocol();

        /**
         * 是否全景直播
         *
         * @return true:全景
         */
        boolean isPanorama();

        /**
         * 推流视频大小
         *
         * @return size
         */
        Size getVideoSize();

        /**
         * 推流视频帧率(max:30)
         *
         * @return fps
         */
        int getVideoFps();

        /**
         * 推流视频码流
         *
         * @return bps
         */
        int getVideoBitrate();

        /**
         * 推流视频编码
         *
         * @return {@link PiVideoEncode}
         */
        @PiVideoEncode
        String getVideoEncode();

        /**
         * 保存直播录像的文件路径 null:不保存
         *
         * @return filePath
         */
        String getRecordPath();

        /**
         * 用户认证，用户名
         */
        String getUserName();

        /**
         * 用户认证，用户密码
         */
        String getUserPassword();

        /**
         * 保存录像时，单个文件最大时长
         *
         * @return 秒
         */
        int getRecordBlockDuration();

        IEncoderExt getEncoderExt();

        /**
         * 返回透传数据
         */
        IDiyData getDiyData();
    }

    @IntDef({
            IProtocol.RTMP,
            IProtocol.RTSP,
    })
    @interface IProtocol {
        int RTMP = 0;
        int RTSP = 1;
    }

    @IntDef({
            PushNetworkQuality.good,
            PushNetworkQuality.normal,
            PushNetworkQuality.bad,
    })
    @interface PushNetworkQuality {
        /**
         * 网络质量好
         */
        int good = 1;
        /**
         * 正常-默认值
         */
        int normal = 0;
        /**
         * 网络质量差
         */
        int bad = -1;
    }

    interface IEncoderExt {
        void config(int var1, int var2, int var3);

        int decodeProcess(byte[] var1, int var2, byte[] var3);

        void release();
    }

    /**
     * 透传数据接口
     */
    interface IDiyData {

    }
}
