package com.pi.pano;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * 视频后处理类,用于将多个鱼眼mp4视频拼接为一个全景视频
 * 你可以多次使用addTask添加多个任务到任务队列,添加到任务队列的任务默认是STITCH_STATE_PAUSE状态,你可以
 * 使用startAllTask开始所有任务,拼接任务同时只能执行一个,因此,只有队列最顶端的任务会变为STITCH_STATE_START
 * 其它任务的状态变为STITCH_STATE_WAIT,当一个任务完成拼接后,该任务会自动从队列中移除
 */
public class StitchingUtil {
    private final String TAG = "StitchingUtil";

    static {
        System.loadLibrary(Config.PIPANO_SO_NAME);
    }

    /**
     * 拼接任务状态
     */
    public enum StitchState {
        /**
         * 已经开始
         */
        STITCH_STATE_START,
        /**
         * 正在暂停
         */
        STITCH_STATE_PAUSING,
        /**
         * 已经暂停
         */
        STITCH_STATE_PAUSE,
        /**
         * 正在排队等待
         */
        STITCH_STATE_WAIT,
        /**
         * 任务发生错误
         */
        STITCH_STATE_ERROR,
        /**
         * 正在开始
         */
        STITCH_STATE_STARTING,
        /**
         * 已经停止
         */
        STITCH_STATE_STOP,
        /**
         * 正在停止
         */
        STITCH_STATE_STOPPING
    }

    /**
     * 拼接任务
     */
    public class Task {

        /**
         * 任务的拼接状态
         */
        volatile StitchState mState = StitchState.STITCH_STATE_PAUSE;
        /**
         * 拼接进度
         */
        volatile float mProgress;
        /**
         * 错误码
         */
        volatile int mErrorCode;
        /**
         * 是否删除任务
         */
        volatile boolean mDeleteStitchingPauseFile = false;
        /**
         * 指定了这个时间戳,即只输出该时间戳的jpeg图像,单位是微秒
         */
        long mStitchPictureInUs = -1;

        @NonNull
        final StitchVideoParams params;

        public Task(@NonNull StitchVideoParams params) {
            this.params = params;
        }

        /**
         * 获取拼接错误码,当拼接状态为STITCH_STATE_ERROR的时候,可通过这个接口获取具体的错误信息
         *
         * @return 错误码
         */
        public int getErrorCode() {
            return mErrorCode;
        }

        /**
         * 开始指定拼接任务,拼接任务同时只能执行一个,如果有其它任务正在执行,那么指定任务的状态为
         * STITCH_STATE_WAIT,否则指定任务的状态为STITCH_STATE_START
         */
        public void startStitchTask() {
            Log.i(TAG, "startStitchTask " + params.srcDir + "  state--->" + mState);
            if (mState == StitchState.STITCH_STATE_PAUSE) {
                changeState(StitchState.STITCH_STATE_WAIT);
            }
            synchronized (mTaskList) {
                mTaskList.notifyAll();
            }
        }

        /**
         * 暂停指定拼接任务,使其状态为STITCH_STATE_PAUSING
         */
        public void pauseStitchTask() {
            Log.i(TAG, "pauseStitchTask " + params.srcDir);
            if (mState == StitchState.STITCH_STATE_START ||
                    mState == StitchState.STITCH_STATE_STARTING ||
                    mState == StitchState.STITCH_STATE_WAIT) {
                changeState(StitchState.STITCH_STATE_PAUSING);
            }
        }

        void deleteStitchingPauseFile() {
            if (mDeleteStitchingPauseFile) {
                if (mStitchingListener != null) {
                    mStitchingListener.onStitchingDeleteDeleting(this);
                }
                File pauseFile = new File(params.srcDir, "stitching_pause.mp4");
                if (pauseFile.exists()) {
                    if (pauseFile.delete()) {
                        mDeleteStitchingPauseFile = false;
                        if (null != mStitchingListener) {
                            mStitchingListener.onStitchingDeleteFinish(this);
                        }
                    }
                }
            }
        }

        /**
         * 从任务队列中删除某个任务
         */
        public void deleteStitchTask() {
            Log.i(TAG, "deleteStitchTask " + params.srcDir);
            mDeleteStitchingPauseFile = true;
            //先删除一下试试,文件有可能被占用,如果被占用,那么一会再删除
            deleteStitchingPauseFile();
            pauseStitchTask();
            synchronized (mTaskList) {
                if (mTaskList.contains(this)) {
                    mTaskList.remove(this);
                }
            }
            if (mStitchingListener != null) {
                mStitchingListener.onStitchingStateChange(this);
            }
        }

        /**
         * 置顶某个拼接任务
         */
        public void topStitchTask() {
            Log.i(TAG, "stickStitchTask " + params.srcDir);
            synchronized (mTaskList) {
                if (mTaskList.getFirst() == this) {
                    return;
                }
                mTaskList.remove(this);
                mTaskList.addFirst(this);
            }
            if (mStitchingListener != null) {
                mStitchingListener.onStitchingStateChange(this);
            }
        }

        public File getFile() {
            return params.srcDir;
        }

        public StitchState getStitchState() {
            return mState;
        }

        public float getProgress() {
            return mProgress;
        }

        void changeState(StitchState state) {
            Log.i(TAG, "changeState " + mState + "=>" + state + " file:" + params.srcDir);
            mState = state;
            if (state == StitchState.STITCH_STATE_ERROR) {
                mTaskList.remove(this);
            }
            if (mStitchingListener != null) {
                mStitchingListener.onStitchingStateChange(this);
            }
        }
    }

    final LinkedList<Task> mTaskList = new LinkedList<>();
    StitchingListener mStitchingListener;
    static String mFirmware;
    static String mArtist;

    /**
     * 构造函数
     */
    public StitchingUtil(Context context) {
        new StitchingThread(context, this).start();
    }

    /**
     * 后拼接事件监听
     *
     * @param listener 监听接口
     */
    public void setStitchingListener(StitchingListener listener) {
        mStitchingListener = listener;
    }

    /**
     * 新增一个拼接任务到队列
     *
     * @param file    指向要拼接的文件夹
     * @param width   拼接后视频的宽
     * @param height  拼接后视频的高
     * @param fps     拼接后视频的帧率
     * @param bitrate 原视频码率
     * @param mime    编码方式
     * @param useFlow 是否使用光流拼接
     */
    public void addStitchTask(File file, int width, int height, int fps, int bitrate, String mime, boolean useFlow) {
        StitchVideoParams params = StitchVideoParams.Factory.createParams(file, width, height, fps, bitrate, mime, useFlow);
        addStitchTask(params);
    }

    public boolean addStitchTask(@NonNull StitchVideoParams params) {
        if (params.width <= 0 || params.height <= 0 || params.fps <= 0 ||
                !params.srcDir.isDirectory()) {
            Log.e(TAG, "addStitchTask params invalid: " + params);
            return false;
        }
        if (!params.srcDir.exists()) {
            Log.e(TAG, "addStitchTask not exit dir: " + params.srcDir.getName());
            return false;
        }
        for (Task task : mTaskList) {
            if (task.params.srcDir.compareTo(params.srcDir) == 0) {
                Log.e(TAG, "addStitchTask already exit dir: " + params.srcDir.getName());
                return true;
            }
        }
        float progress = 0;
        File pauseFile = new File(stitchingPath(params.srcDir, params.targetDir, true));
        if (pauseFile.exists()) {
            if (!params.ignoreTransFile) {
                progress = getDuring(pauseFile.getAbsolutePath()) * 100f /
                        getDuring(new File(params.srcDir, "0.mp4").getAbsolutePath());
            } else {
                boolean success = pauseFile.delete();
                Log.w(TAG, "addStitchTask del by ignoreTransFile :" + pauseFile + ",result:" + success);
            }
        }
        params.progress = progress;
        Log.d(TAG, "addStitchTask ==> " + params);
        return mTaskList.add(new Task(params));
    }

    /**
     * 获取拼接任务队列
     *
     * @return 拼接任务队列
     */
    public List<Task> getStitchTaskQueue() {
        return mTaskList;
    }

    /**
     * 开始所有拼接任务,将除了STITCH_STATE_START状态的所有任务的状态设置位STITCH_STATE_WAIT
     */
    public void startAllStitchTask() {
        Log.i(TAG, "startAllStitchTask");
        for (Task task : mTaskList) {
            if (task.getStitchState() != StitchState.STITCH_STATE_PAUSING) {
                task.changeState(StitchState.STITCH_STATE_WAIT);
            }
        }
        synchronized (mTaskList) {
            mTaskList.notifyAll();
        }
    }

    /**
     * 暂停所有拼接任务,除了错误状态的任务以外
     */
    public void pauseAllStitchTask() {
        Log.i(TAG, "pauseAllStitchTask");
        for (Task task : mTaskList) {
            if (task.getStitchState() == StitchState.STITCH_STATE_START ||
                    task.getStitchState() == StitchState.STITCH_STATE_STARTING ||
                    task.getStitchState() == StitchState.STITCH_STATE_WAIT) {
                task.changeState(StitchState.STITCH_STATE_PAUSING);
            }
        }
    }

    /**
     * 删除所有拼接任务
     */
    public void deleteAllStitchTask() {
        Log.i(TAG, "deleteAllStitchTask");
        for (Task task : mTaskList) {
            task.mDeleteStitchingPauseFile = true;
            task.deleteStitchingPauseFile();
        }
        pauseAllStitchTask();
        mTaskList.clear();
    }

    static long getDuring(String filename) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(filename);
            String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Long.parseLong(durationStr) * 1000;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mmr.release();
        }
        return 1;
    }

    Task getNextTask() {
        synchronized (mTaskList) {
            for (Task task : mTaskList) {
                if (task.getStitchState() == StitchState.STITCH_STATE_START ||
                        task.getStitchState() == StitchState.STITCH_STATE_STARTING ||
                        task.getStitchState() == StitchState.STITCH_STATE_WAIT) {
                    return task;
                }
            }
        }
        return null;
    }

    void checkHaveNextTask() {
        synchronized (mTaskList) {
            while (getNextTask() == null) {
                try {
                    mTaskList.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static String stitchingPath(File srcFile, String targetDir, boolean pause) {
        String stitchingPath;
        if (TextUtils.isEmpty(targetDir)) {
            stitchingPath = srcFile.getAbsolutePath() + File.separator;
        } else {
            stitchingPath = targetDir + File.separator + srcFile.getName() + "_";
        }
        stitchingPath += pause ? StitchingRecorder.FILE_STITCHING_PAUSE : StitchingRecorder.FILE_STITCHING;
        return stitchingPath;
    }

}
