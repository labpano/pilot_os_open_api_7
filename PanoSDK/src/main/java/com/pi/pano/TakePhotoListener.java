package com.pi.pano;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiPhotoFileFormat;

import java.io.File;

/**
 * 拍照回调
 */
public abstract class TakePhotoListener {
    /**
     * 拍照参数
     */
    @NonNull
    public PhotoParams mParams;
    /**
     * 拍照后生成的未拼接文件
     */
    public File mUnStitchFile;

    /**
     * 拍照采集开始。
     * 拍照可能采集多次。
     *
     * @param index 采集索引
     */
    protected void onTakePhotoStart(int index) {
    }

    /**
     * 捕获照片完成(仅捕获照片，不包括保存等其他图片处理)
     */
    protected void onCapturePhotoEnd() {

    }

    void dispatchTakePhotoComplete(int errorCode) {
        onTakePhotoComplete(errorCode);
    }

    /**
     * 拍照（采集）完成。
     *
     * @param errorCode 错误码
     */
    protected void onTakePhotoComplete(int errorCode) {
        if (errorCode == 0 && mParams.createThumb) {
            // 注入 exif 缩略图
            if (null != mUnStitchFile && mUnStitchFile.isFile() &&
                    mUnStitchFile.getName().endsWith(PiPhotoFileFormat.jpg) &&
                    mParams.hdrCount <= 0) {
                ThumbnailGenerator.injectExifThumbnailForImage(mUnStitchFile);
            }
        }
        if (errorCode != 0 && mParams.isHdr()) {
            PilotSDK.setPanoEnable(true);
        }
    }
}
