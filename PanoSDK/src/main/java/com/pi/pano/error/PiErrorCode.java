package com.pi.pano.error;

import android.view.ViewGroup;

import com.pi.pano.DefaultChangeResolutionListener;
import com.pi.pano.PanoSDKListener;
import com.pi.pano.PiCallback;
import com.pi.pano.IPreviewManager;
import com.pi.pano.ResolutionParams;

/**
 * 统一错误码,用于{@link PiCallback#onError(PiError)}
 */
public @interface PiErrorCode {

    //region base

    int SUCCESS = 0;

    int UN_KNOWN = -1;
    /**
     * pano未初始化 ,建议重新初始化 {@link  IPreviewManager#initPreviewView(ViewGroup, int, boolean, PanoSDKListener)}
     */
    int NOT_INIT = 1;
    /**
     * 操作超时
     */
    int TIME_OUT = 5;

    //endregion

    //region release

    int ALREADY_RELEASE = 10;
    int FILTER_RELEASE_ING = 11;

    //endregion

    /**
     * 相机未打开或者已经关闭了，需要重新刷新分辨率{@link IPreviewManager#changeResolution(ResolutionParams, DefaultChangeResolutionListener)}
     */
    int CAMERA_NOT_OPENED = 20;
    /**
     * 相机会话未创建，或已关闭，需要重新开启预览{@link IPreviewManager#restorePreview(PiCallback)}
     */
    int CAMERA_SESSION_NOT_CREATE = 21;
    /**
     * camera session配置失败 ,建议 重新开启预览{@link IPreviewManager#restorePreview(PiCallback)}
     */
    int CAMERA_SESSION_CONFIGURE_FAILED = 22;
    /**
     * camera session 更新失败 ,建议重新开启预览 {@link IPreviewManager#restorePreview(PiCallback)}
     */
    int CAMERA_SESSION_UPDATE_FAILED = 23;
    /**
     * camera多次出帧失败
     */
    int CAMERA_MANY_CAPTURE_FAILED = 24;
    /**
     * 拍照请求失败
     */
    int TAKE_PHOTO_CAPTURE_FAILED = 31;
    /**
     * hdr拍照完毕，合成时失败
     */
    int HDR_PHOTO_STACK_FAILED = 35;
    /**
     * hdr合成时，发现某张照片加载bitmap失败
     */
    int HDR_PHOTO_STACK_BITMAP = 36;

    /**
     * hdr照片丢失(可能拍照中某些照片失败 或 保存文件失败)
     */
    int HDR_PHOTO_LOSE = 37;
    /**
     * hdr捕获照片成功后，恢复预览失败
     */
    int HDR_PHOTO_RESTORE_PREVIEW_ERROR = 38;

    /**
     * 录制视频，编码器初始化配置失败
     */
    int RECORD_ENCODE_CONFIGURE_FAILED = 40;
    /**
     * 录制视频，用于创建camera session 输出的surface非法
     */
    int RECORD_CAMERA_OUT_SURFACE_ILLEGAL = 41;
    /**
     * 录像中--写入文件失败(可能是空间不足，文件被删除，其他IO问题)
     */
    int RECORD_WRITE_FILE_ERROR = 42;
    //region resolution
    /**
     * 数据[分辨率,fps,cameraId]无变化，不需要切换
     */
    int RESOLUTION_DATA_NO_CHANGE = 50;

    //endregion
}
