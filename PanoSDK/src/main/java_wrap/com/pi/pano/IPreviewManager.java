package com.pi.pano;

import android.view.Surface;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiAntiMode;
import com.pi.pano.annotation.PiEncodeSurfaceType;
import com.pi.pano.wrap.ProParams;
import com.pi.pano.wrap.annotation.PiPreviewType;

/**
 * pano 预览管理接口
 */
public interface IPreviewManager {

    /**
     * 初始化预览 见{@link #initPreviewView(ViewGroup, int, PanoSDKListener)},cameraCount=1
     */
    void initPreviewView(ViewGroup parent, PanoSDKListener listener);

    /**
     * 初始化预览 见{@link #initPreviewView(ViewGroup, int, boolean, PanoSDKListener)},lensProtected=false
     */
    void initPreviewView(ViewGroup parent, @PiPreviewType int previewType, PanoSDKListener listener);

    /**
     * 初始化预览
     *
     * @param parent        装载View的容器
     * @param previewType   相机预览类型{@link PiPreviewType},默认照片{@link PiPreviewType#IMAGE}
     * @param lensProtected 是否使用保护镜
     * @param listener      状态监听
     */
    void initPreviewView(ViewGroup parent, @PiPreviewType int previewType, boolean lensProtected, PanoSDKListener listener);

    /**
     * 切换分辨率
     * <p>如果是首次切换或者{@link PilotSDK#release(PiCallback)}后，
     * 或者是{@link PanoSDKListener#onPanoRelease()}以后，那么切换成功后，
     * 则需要重新初始化参数配置 :
     * <p>.{@link #setAntiMode(String)}
     * <p>.{@link #setSteadyAble(boolean)}
     * <p>.{@link #setSteadyFollow(boolean, boolean)}
     * <p>.{@link #setInPhotoHdr(boolean)}
     *
     * @param params   分辨率参数：{@link ResolutionParams.Factory#createParamsForPhoto(String, ProParams)}...
     * @param listener 监听,其中错误码见{@link com.pi.pano.error.PiErrorCode}
     */
    void changeResolution(@NonNull ResolutionParams params, @NonNull DefaultChangeResolutionListener listener);

    /**
     * 恢复预览
     *
     * @param callback 回调
     */
    void restorePreview(@Nullable PiCallback callback);

    /**
     * 拍8K及以照片前必须调用，
     * <p>注：拍照结束后，如果未进行连续拍照，则需要调用 {@link #restorePreview(PiCallback)}恢复预览
     *
     * @param callback 回调
     */
    void startPreviewWithTakeLargePhoto(@NonNull PiCallback callback);

    /**
     * 设置预览绘制状态
     *
     * @param enable true:正常绘制 ,false:绘制一帧模糊画面(一般在高帧率时使用)
     */
    void setPreviewEnable(boolean enable);

    /**
     * 专业模式相关 参数配置
     *
     * @param params   参数
     * @param callback 回调
     */
    void setProParams(@NonNull ProParams params, @Nullable PiCallback callback);

    /**
     * 是否处理hdr拍照中
     *
     * @param hdrAble true:处理
     */
    void setInPhotoHdr(boolean hdrAble);

    /**
     * 防抖设置
     *
     * @param open 开启防抖
     */
    void setSteadyAble(boolean open);

    /**
     * 设置防抖是否跟随
     *
     * @param isFollow true:跟随相机 ,false:固定
     * @param isPano   true:全景预览 ，false:平面预览
     */
    void setSteadyFollow(boolean isFollow, boolean isPano);

    /**
     * 设置抗频闪
     *
     * @param mode 模式
     */
    void setAntiMode(@PiAntiMode String mode);

    /**
     * 当前camera 是否在预览中
     *
     * @return true:camera预览中 , false:sdk未初始化 or camera未打开|异常 or cameraSession异常
     */
    boolean isCameraPreviewing();

    /**
     * 设置用于编码surface ,SDK会渲染图形到这个surface上,并由MediaRecorder或MediaCodec编码
     *
     * @param surface     这个surface一般来自MediaRecorder.getSurface()或MediaCodec.createInputSurface(),传null表示释放
     * @param surfaceType 类型：{@link PiEncodeSurfaceType}
     */
    void setEncodeInputSurface(@Nullable Surface surface, @PiEncodeSurfaceType int surfaceType);

    /**
     * @return 当前预览的帧率
     */
    int getFps();



}
