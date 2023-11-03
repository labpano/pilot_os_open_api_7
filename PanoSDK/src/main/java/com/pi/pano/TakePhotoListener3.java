package com.pi.pano;

public interface TakePhotoListener3 {
    /**
     * 拍照开始
     */
    void onTakePhotoStart();

    /**
     * 拍照完成
     *
     * @param filepath 文件路径
     */
    void onTakePhotoComplete(String filepath);

    /**
     * 拍照错误
     *
     * @param errorCode 错误码
     */
    void onTakePhotoError(int errorCode);
}
