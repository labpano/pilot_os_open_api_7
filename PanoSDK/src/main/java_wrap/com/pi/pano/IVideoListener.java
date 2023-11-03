package com.pi.pano;

import androidx.annotation.Nullable;

import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;
import com.pi.pano.VideoParams;

/**
 * 录像监听
 */
public interface IVideoListener {

    /**
     * 录像-采集开始
     *
     * @param params 录像参数
     */
    void onRecordStart(VideoParams params);

    /**
     * 录像-采集完成
     *
     * @param filepath 录像文件路径
     */
    void onRecordStop(@Nullable String filepath);

    /**
     * 录像错误
     *
     * @param error    错误
     *                 {@link PiErrorCode#NOT_INIT}, {@link PiErrorCode#CAMERA_NOT_OPENED} ,
     *                 {@link PiErrorCode#CAMERA_SESSION_NOT_CREATE}, {@link PiErrorCode#UN_KNOWN} ,
     *                 {@link PiErrorCode#CAMERA_SESSION_UPDATE_FAILED} ,
     *                 {@link PiErrorCode#CAMERA_SESSION_CONFIGURE_FAILED},
     *                 {@link PiErrorCode#RECORD_WRITE_FILE_ERROR}
     * @param filepath 录像文件路径
     */
    void onRecordError(PiError error, @Nullable String filepath);
}
