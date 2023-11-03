package com.pi.pano;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiResolution;
import com.pi.pano.wrap.IPhotoListener;

/**
 * 拍照，录像管理接口
 */
public interface ICaptureManager {

    String sPhotoMaxResolution = PiResolution._5_7K;

    /**
     * 拍照
     *
     * @param params   拍照参数-构造：{@link PhotoParams.Factory#createParams(int, String, String)}
     * @param listener 回调
     */
    void takePhoto(@NonNull PhotoParams params, @NonNull IPhotoListener listener);

    /**
     * 从当前屏幕获取画面拍照
     *
     * @param filepath  保存路径
     * @param width     照片宽
     * @param height    照片高
     * @param listener3 回调
     */
    void takeScreenPhoto(String filepath, int width, int height, @NonNull TakePhotoListener3 listener3);

    /**
     * 开始录像
     *
     * @param params   录像参数 见：{@link VideoParams.Factory#create(IRecordFilenameProxy, String, int, String, int)}
     * @param listener 回调接口
     */
    void startRecord(@NonNull VideoParams params, IVideoListener listener);

    /**
     * 停止录像
     *
     * @param continueRecord 是否继续录像（分段录像） true:停止后将再开始录像, false：停止后不继续录像一定要填false
     * @param listener       回调
     */
    void stopRecord(boolean continueRecord, IVideoListener listener);

}
