package com.pi.pano;

import android.util.Size;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * 播放管理接口(照片加载，视频播放)
 */
public interface IPlayerManager {


    /**
     * 播放全景照片
     *
     * @param parent   装载播放View容器
     * @param file     播放文件路径
     * @param listener 回调接口
     * @return 播放控制接口
     */
    MediaPlayerSurfaceView.IPlayControl playImage(@NonNull ViewGroup parent, @NonNull File file,
                                                  @NonNull MediaPlayerSurfaceView.ImagePlayListener listener);

    /**
     * 播放全景照片
     *
     * @param parent        装载播放View容器
     * @param file          播放文件路径
     * @param lensProtected true:相机使用了保护镜 ,默认false
     * @param listener      回调接口
     * @return 播放控制接口
     */
    MediaPlayerSurfaceView.IPlayControl playImage(@NonNull ViewGroup parent, @NonNull File file, boolean lensProtected,
                                                  @NonNull MediaPlayerSurfaceView.ImagePlayListener listener);

    /**
     * 播放视频
     *
     * @param parent   装载播放View容器
     * @param file     播放文件路径(已拼接：传mp4路径 ，未拼接：传文件目录)
     * @param listener 回调接口
     * @return 播放控制接口
     */
    MediaPlayerSurfaceView.IVideoPlayControl playVideo(@NonNull ViewGroup parent, @NonNull File file,
                                                       @NonNull MediaPlayerSurfaceView.VideoPlayListener listener);

    /**
     * 播放视频
     *
     * @param parent        装载播放View容器
     * @param file          播放文件路径(已拼接：传mp4路径 ，未拼接：传文件目录)
     * @param lensProtected true:相机使用了保护镜 ,默认false
     * @param size          surface大小, null:默认使用parent大小(未获取到，则使用全屏)
     * @param listener      回调接口
     * @return 播放控制接口
     */
    MediaPlayerSurfaceView.IVideoPlayControl playVideo(@NonNull ViewGroup parent, @NonNull File file, boolean lensProtected,
                                                       @Nullable Size size, @NonNull MediaPlayerSurfaceView.VideoPlayListener listener);

    /**
     * 释放Player,退出UI界面后需要调用
     */
    void release();

    /**
     * 播放时，是否开启陀螺仪
     *
     * @param open true:打开
     */
    void openGyroscope(boolean open);

    SurfaceView getPlayerView();

}
