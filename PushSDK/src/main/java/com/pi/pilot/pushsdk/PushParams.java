package com.pi.pilot.pushsdk;

import android.util.Size;

import androidx.annotation.Nullable;

import com.pi.pano.IPushManager;
import com.pi.pano.annotation.PiVideoEncode;

public class PushParams implements IPushManager.IParams {

    public PushParams(Size videoSize, int videoFps, int videoBitrate, String pushUrl) {
        this.videoSize = videoSize;
        this.videoFps = videoFps;
        this.videoBitrate = videoBitrate;
        this.pushUrl = pushUrl;
    }

    public Size videoSize;
    public int videoFps;
    public int videoBitrate;

    public int audioChannelNum = 2;
    public int audioSampleRate = 48_000;
    public int audioBitsPerSample = 16;
    public int audioBitrate = 128_000;
    // end-other info

    //------------非推流库参数------------------
    /***是否为全景直播 */
    public boolean isPanorama = true;
    // end-非推流库参数

    public String pushUrl;
    @Nullable
    public String username;
    @Nullable
    public String userPassword;
    @IPushManager.IProtocol
    public int protocol = IPushManager.IProtocol.RTMP;
    @PiVideoEncode
    public String videoEncode = PiVideoEncode.h_264;
    public IPushManager.IDiyData diyData;
    @Nullable
    public String recordPath;
    /**
     * 录制的单个文件最大时长，单位：秒
     */
    public int recordBlockDuration = 5 * 60;

    public IPushManager.IEncoderExt mIEncoderExt;

    @Override
    public String getPushUrl() {
        return pushUrl;
    }

    @Override
    public int getProtocol() {
        return protocol;
    }

    @Override
    public boolean isPanorama() {
        return isPanorama;
    }

    @Override
    public Size getVideoSize() {
        return videoSize;
    }

    @Override
    public int getVideoFps() {
        return videoFps;
    }

    @Override
    public int getVideoBitrate() {
        return videoBitrate;
    }

    @Override
    public String getVideoEncode() {
        return videoEncode;
    }

    @Override
    public String getRecordPath() {
        return recordPath;
    }

    @Override
    public String getUserName() {
        return username;
    }

    @Override
    public String getUserPassword() {
        return userPassword;
    }

    @Override
    public int getRecordBlockDuration() {
        return recordBlockDuration;
    }

    @Override
    public IPushManager.IEncoderExt getEncoderExt() {
        return mIEncoderExt;
    }

    public IPushManager.IDiyData getDiyData() {
        return diyData;
    }
}
