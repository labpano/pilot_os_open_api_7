package com.pi.pano.wrap;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.MediaPlayerSurfaceView;

import java.io.File;

/**
 * 非全景视频播放
 */
public class VideoPlayerView extends SurfaceView {
    private static final String TAG = VideoPlayerView.class.getSimpleName();

    public VideoPlayerView(Context context) {
        super(context);
        init();
    }

    public VideoPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VideoPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public VideoPlayerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    protected void init() {
        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Log.d(TAG, "surfaceDestroyed :" + mPlay);
                if (mPlay != null) {
                    mPlay.release();
                    mPlay = null;
                }
            }
        });
    }

    private MediaPlayerSurfaceView.IVideoPlayControl mPlay;

    public MediaPlayerSurfaceView.IVideoPlayControl playVideo(File file, MediaPlayerSurfaceView.VideoPlayListener listener) {
        if (null != mPlay) {
            mPlay.release();
            mPlay = null;
        }
        mPlay = new VideoPlayControl(file, listener);
        return mPlay;
    }

    private void waitSurfaceInit(long timeout) {
        Surface surface = getHolder().getSurface();
        if (surface.isValid()) {
            return;
        }
        long start = System.currentTimeMillis();
        while (true) {
            if ((System.currentTimeMillis() - start) > timeout) {
                break;
            }
            if (!surface.isValid()) {
                SystemClock.sleep(500);
                continue;
            }
            break;
        }
    }

    public static boolean isPanoVideo(@NonNull int[] ret) {
        if (ret[1] != 0) {
            return (ret[0] / ret[1]) == 2;
        }
        return false;
    }

    @Nullable
    public static int[] obtainVideoSize(File file) {
        int[] ret = null;
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            String video_width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String video_height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (!TextUtils.isEmpty(video_width) && !TextUtils.isEmpty(video_height)) {
                ret = new int[2];
                ret[0] = Integer.parseInt(video_width);
                ret[1] = Integer.parseInt(video_height);
            }
        } catch (Exception ex) {
            Log.d(TAG, "obtainVideoSize,ex:" + ex);
            ex.printStackTrace();
        }
        return ret;
    }

    /**
     * 拼接视频播放。
     * 直接使用系统MediaPlayer解析。
     */
    protected class VideoPlayControl implements MediaPlayerSurfaceView.IVideoPlayControl {
        private final String TAG = VideoPlayerView.TAG + "$Video";
        private final String filepath;
        private final MediaPlayerSurfaceView.VideoPlayListener listener;
        private final Handler mHandler;
        @MediaPlayerSurfaceView.VideoPlayState
        private int state = MediaPlayerSurfaceView.VideoPlayState.release;
        private MediaPlayer mMyMediaPlayer;
        private final HandlerThread mThread;

        protected VideoPlayControl(File file, @Nullable MediaPlayerSurfaceView.VideoPlayListener listener) {
            this.filepath = file.getAbsolutePath();
            this.listener = listener;
            mThread = new HandlerThread("play:" + file.getName());
            mThread.start();
            mHandler = new Handler(mThread.getLooper());
            mHandler.post(this::initPlayer);
        }

        private final Runnable mTickRunnable = () -> {
            if (null != mMyMediaPlayer) {
                notifyStateTimeChange();
            }
            if (state == MediaPlayerSurfaceView.VideoPlayState.started) {
                loopTick();
            }
        };

        private void loopTick() {
            //Log.d(TAG, "loopTick");
            mHandler.removeCallbacks(mTickRunnable);
            mHandler.postDelayed(mTickRunnable, 100);
        }

        private void initPlayer() {
            Log.d(TAG, "initPlayer...");
            waitSurfaceInit(2000);
            Surface surface = getHolder().getSurface();
            if (!surface.isValid()) {
                Log.e(TAG, "initPlayer,PiPano not init.");
                if (null != listener) {
                    listener.onPlayControlInit(this, 1);
                }
                return;
            }
            mMyMediaPlayer = new MediaPlayer();
            mMyMediaPlayer.setSurface(surface);
            mMyMediaPlayer.reset();
            try {
                mMyMediaPlayer.setDataSource(filepath);
                mMyMediaPlayer.prepare();
                mMyMediaPlayer.seekTo(0); // seek to start,show first frame.
            } catch (Exception ex) {
                Log.e(TAG, "initPlayer,ex:" + ex);
                ex.printStackTrace();
                if (null != listener) {
                    listener.onPlayControlInit(this, 1);
                }
                return;
            }
            mMyMediaPlayer.setOnSeekCompleteListener(mp -> {
                if (state == MediaPlayerSurfaceView.VideoPlayState.started) {
                    checkState();
                }
            });
            mMyMediaPlayer.setOnCompletionListener(mp -> changeState(MediaPlayerSurfaceView.VideoPlayState.completed));
            if (null != listener) {
                listener.onPlayControlInit(this, 0);
            }
            changeState(MediaPlayerSurfaceView.VideoPlayState.prepared);
            if (null != listener) {
                listener.onPlayTimeChange(this, 0f, getDuration());
            }
            Log.d(TAG, "initPlayer,ok");
        }

        private void releasePlayer() {
            Log.d(TAG, "releasePlayer...");
            if (mMyMediaPlayer != null) {
                if (mMyMediaPlayer.isPlaying()) {
                    mMyMediaPlayer.stop();
                }
                mMyMediaPlayer.release();
                mMyMediaPlayer = null;
                if (null != listener) {
                    listener.onPlayControlRelease(this);
                }
            }
            Log.d(TAG, "releasePlayer,ok");
        }

        @Override
        public int getState() {
            return state;
        }

        @Override
        public void play() {
            if (state != MediaPlayerSurfaceView.VideoPlayState.started) {
                Log.d(TAG, "play");
                changeState(MediaPlayerSurfaceView.VideoPlayState.started);
                mHandler.post(this::checkState);
            }
        }

        @Override
        public void pause() {
            if (state != MediaPlayerSurfaceView.VideoPlayState.paused) {
                Log.d(TAG, "paused");
                changeState(MediaPlayerSurfaceView.VideoPlayState.paused);
                mHandler.post(this::checkState);
            }
        }

        @Override
        public void seek(float progress) {
            if (null != mMyMediaPlayer) {
                Log.d(TAG, "seek," + progress);
                int duration = mMyMediaPlayer.getDuration();
                mMyMediaPlayer.seekTo((int) (progress * duration));
            }
        }

        @Override
        public void release() {
            if (state != MediaPlayerSurfaceView.VideoPlayState.release) {
                Log.d(TAG, "release...");
                mHandler.removeCallbacksAndMessages(null);
                changeState(MediaPlayerSurfaceView.VideoPlayState.release);
                mHandler.post(this::checkState);
                try {
                    mThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "release,end");
            }
        }

        @Override
        public float getDuration() {
            return null != mMyMediaPlayer ? mMyMediaPlayer.getDuration() : 0;
        }

        @Override
        public float getCurTime() {
            return null != mMyMediaPlayer ? mMyMediaPlayer.getCurrentPosition() : 0;
        }

        private void checkState() {
            if (null == mMyMediaPlayer) {
                return;
            }
            Log.d(TAG, "checkState,state:" + state);
            switch (state) {
                case MediaPlayerSurfaceView.VideoPlayState.started: {
                    if (!mMyMediaPlayer.isPlaying()) {
                        mMyMediaPlayer.start();
                        loopTick();
                    }
                }
                break;
                case MediaPlayerSurfaceView.VideoPlayState.paused: {
                    if (mMyMediaPlayer.isPlaying()) {
                        mMyMediaPlayer.pause();
                    }
                }
                break;
                case MediaPlayerSurfaceView.VideoPlayState.release: {
                    releasePlayer();
                    mHandler.getLooper().quitSafely();
                }
                break;
                default:
                    break;
            }
        }

        private void changeState(int state) {
            if (this.state != state) {
                this.state = state;
                notifyStateChange();
            }
        }

        private void notifyStateChange() {
            if (listener != null) {
                listener.onPlayStateChange(this, state);
            }
            boolean keepScreenOn = MediaPlayerSurfaceView.VideoPlayState.started == state;
            if (Looper.getMainLooper().isCurrentThread()) {
                setKeepScreenOn(keepScreenOn);
            } else {
                post(() -> setKeepScreenOn(keepScreenOn));
            }
        }

        private void notifyStateTimeChange() {
            if (listener != null) {
                listener.onPlayTimeChange(this, getCurTime(), getDuration());
            }
        }
    }
}
