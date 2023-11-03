package com.pi.pano;

import androidx.annotation.NonNull;

import com.pi.pano.wrap.PhotoStitchWrap;

import java.io.File;

/**
 * 拼接管理接口
 */
public interface IStitchManager {

    /**
     * 拼接照片
     *
     * @param params 拼接参数：{@link PhotoStitchWrap.Factory#create(String, boolean)}
     * @return 拼接后文件路径 or null（拼接失败）
     */
    String stitchPhoto(@NonNull PhotoStitchWrap.PhotoStitchParams params);

    /**
     * 设置视频拼接监听
     *
     * @param listener 回调
     */
    void setVideoStitchListener(StitchingListener listener);

    /**
     * 添加视频拼接(添加到视频队列中,同时只会执行一个)
     *
     * @param params 拼接参数：{@link StitchVideoParams.Factory#createParams(File, int, int, int, int)}
     * @return 任务是否添加成功
     */
    boolean addVideoTask(@NonNull StitchVideoParams params);

    /**
     * 开始视频拼接任务
     */
    void startVideoStitchTask();

    /**
     * 暂停视频拼接任务
     */
    void pauseVideoStitchTask();

    /**
     * 删除视频拼接任务
     */
    void deleteVideoStitchTask();
}
