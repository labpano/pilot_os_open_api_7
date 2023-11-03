package com.pi.pano;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiEncodeSurfaceType;
import com.pi.pano.annotation.PiLensCorrectionMode;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 集成MediaPlayer的SurfaceView。
 * 用于播放拼接的照片、拼接的视频。
 */
public class MediaPlayerSurfaceView extends PanoSurfaceView {
    private final static String TAG = MediaPlayerSurfaceView.class.getSimpleName();

    private static final int PREVIEW_SIZE_MAX_WIDTH = PilotSDK.CAMERA_COUNT == 4 ? 6000 : 4096;
    private static final int PREVIEW_SIZE_MAX_HEIGHT = PilotSDK.CAMERA_COUNT == 4 ? 3000 : 2048;
    /**
     * 需要忽略视频前面解码的帧数
     */
    private static final int MAX_IGNORE_DECODE_FRAME_NUM = 15;
    /**
     * 播放视频时，需要忽略绘制的 前面帧数
     */
    private static final int MAX_IGNORE_DRAW_FRAME_NUM = 1;

    private final List<Surface> mSurfaceList = new CopyOnWriteArrayList<>();
    /**
     * 用于加载视频预览画面，帧数过滤检测
     */
    private static final SkipFrame sSkipFrameWithPreview =
            new SkipFrame(MAX_IGNORE_DECODE_FRAME_NUM, MAX_IGNORE_DRAW_FRAME_NUM);

    public MediaPlayerSurfaceView(Context context, int cameraCount) {
        this(context, cameraCount, null);
    }

    public MediaPlayerSurfaceView(Context context, int cameraCount, AttributeSet attrs) {
        super(context, attrs, cameraCount);
        mLensCorrectionMode = PiLensCorrectionMode.FISH_EYE;
    }

    public void setLensCorrectionMode(@PiLensCorrectionMode int mode) {
        mLensCorrectionMode = mode;
    }

    private boolean mUseGyroscopeForPlayer;


    private final Object panoLock = new Object();

    /**
     * 播放是否使用陀螺仪
     */
    public void useGyroscopeForPlayer(boolean open) {
        mUseGyroscopeForPlayer = open;
        if (mPiPano != null) {
            mPiPano.useGyroscopeForPlayer(mUseGyroscopeForPlayer);
        }
    }

    @Override
    public void onPiPanoInit(@NonNull PiPano pano) {
        super.onPiPanoInit(pano);
        Log.d(TAG, "onPiPanoInit");
        if (mPiPano != null) {
            mPiPano.useGyroscopeForPlayer(mUseGyroscopeForPlayer);
        }
    }

    @Override
    protected void onSurfaceCreatedImpl(SurfaceHolder holder) {
//        super.onSurfaceCreatedImpl(holder);
        if (mPiPano != null) {
            synchronized (panoLock) {
                try {
                    Log.w(TAG, "onSurfaceCreatedImpl wait");
                    panoLock.wait(8000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.e(TAG, "onSurfaceCreatedImpl init");
        mPiPano = new OpenGLThread(this, mContext, false, mCameraCount);
        mPiPano.setEncodeSurface(holder.getSurface(), PiEncodeSurfaceType.PREVIEW);
    }

    @Override
    protected void onSurfaceDestroyedImpl() {
//        super.onSurfaceDestroyedImpl();
        Log.d(TAG, "onSurfaceDestroyedImpl " + mPiPano + ",," + this);
        if (mLifecycleManager != null) {
            mLifecycleManager.release();
            mLifecycleManager = null;
        }
        if (mPiPano != null) {
            mPiPano.release(null);
        }
    }

    @Override
    public void onPiPanoChangeCameraResolution(@NonNull ChangeResolutionListener listener) {
    }

    @Override
    public void onPiPanoEncoderSurfaceUpdate(@NonNull PiPano pano, long timestamp, boolean isFrameSync) {
        if (mLensCorrectionMode != PiLensCorrectionMode.FISH_EYE) {
            if (!sSkipFrameWithPreview.filter()) {
                sSkipFrameWithPreview.setCheck(false);
                pano.drawLensCorrectionFrame(mLensCorrectionMode, true, timestamp, false);
            }
        }
        if (timestamp > 0 && sSkipFrameWithPreview.check) {
            sSkipFrameWithPreview.skipDrawFrame--;
        }
    }

    @Override
    public void onPiPanoDestroy(@NonNull PiPano pano) {
        Log.d(TAG, "onPiPanoDestroy");
        if (mPlay != null) {
            mPlay.release();
            mPlay = null;
        } else {
            releaseGlSurface();
        }
        super.onPiPanoDestroy(pano);
        synchronized (panoLock) {
            panoLock.notifyAll();
        }
        Log.d(TAG, "onPiPanoDestroy end " + pano);
    }


    private synchronized Surface obtainGlSurface(int index) {
        if (!mSurfaceList.isEmpty() && index == 0) {
            Log.e(TAG, "obtainGlSurface, last surface is not release!");
            throw new RuntimeException("last surface is not release!");
        }
        Log.d(TAG, "obtainGlSurface");
        Surface surface = new Surface(mPiPano.mFrameAvailableTask[index].mSurfaceTexture);
        mSurfaceList.add(surface);
        return surface;
    }

    private synchronized void releaseGlSurface() {
        for (int i = 0; i < mSurfaceList.size(); i++) {
            Surface surface = mSurfaceList.get(i);
            Log.d(TAG, "releaseGlSurface :" + i);
            surface.release();
        }
        mSurfaceList.clear();
    }

    private synchronized void drawBitmap(Bitmap bitmap) {
        releaseGlSurface();
        if (mPiPano != null && mPiPano.mFrameAvailableTask != null && mPiPano.mFrameAvailableTask[0].mSurfaceTexture != null) {
            Surface surface = obtainGlSurface(0);
            if (bitmap != null) {
                Log.d(TAG, "drawBitmap...");
                mPiPano.mFrameAvailableTask[0].mSurfaceTexture.setDefaultBufferSize(bitmap.getWidth(), bitmap.getHeight());
                Canvas canvas = surface.lockHardwareCanvas();
                canvas.drawBitmap(bitmap, 0, 0, null);
                surface.unlockCanvasAndPost(canvas);
                bitmap.recycle();
                Log.d(TAG, "drawBitmap,ok");
            }
        }
    }

    private void waitPanoInit(long timeout) {
        long start = System.currentTimeMillis();
        while (true) {
            if (null == mPiPano || !mPiPano.mHasInit) {
                if ((System.currentTimeMillis() - start) > timeout) {
                    break;
                }
                SystemClock.sleep(500);
                continue;
            }
            break;
        }
    }

    private IPlayControl mPlay;

    public interface IPlayControl {
        void play();

        void release();
    }

    public interface ImagePlayListener {
        void onPlayFailed(int ret);

        void onPlayCompleted(File file);
    }

    /**
     * 播放图片
     *
     * @param file       图片文件
     * @param isStitched 是否拼接
     * @param listener   完成监听
     * @return null:时不能播放
     */
    public IPlayControl playImage(File file, boolean isStitched, @Nullable ImagePlayListener listener) {
        Log.d(TAG, "playImage," + file);
        if (null != mPlay) {
            mPlay.release();
            mPlay = null;
        }
        if (!file.isFile()) {
            return null;
        }
        mPlay = new ImageLoader(file.getAbsoluteFile(), isStitched, listener);
        mPlay.play();
        return mPlay;
    }

    private class ImageLoader implements Runnable, IPlayControl {
        private final String TAG = MediaPlayerSurfaceView.TAG + "$ImageLoader";
        private final File file;
        private final boolean isStitched;
        private final ImagePlayListener listener;
        private final Handler mHandler;
        private final HandlerThread mThread;

        private boolean isQuit = false;

        private ImageLoader(File file, boolean isStitched, @Nullable ImagePlayListener listener) {
            this.file = file;
            this.isStitched = isStitched;
            this.listener = listener;
            mThread = new HandlerThread("play:" + file.getName());
            mThread.start();
            mHandler = new Handler(mThread.getLooper());
        }

        @Override
        public void run() {
            waitPanoInit(2000);
            if (isQuit) {
                return;
            }
            if (null == mPiPano || !mPiPano.mHasInit) {
                if (null != listener) {
                    listener.onPlayFailed(1);
                }
                return;
            }
            if (PilotSDK.CAMERA_COUNT == 2) {
                if (isStitched) {
                    setLensCorrectionMode(PiLensCorrectionMode.STITCHED_2);
                } else {
                    setLensCorrectionMode(PiLensCorrectionMode.PANO_MODE_4);
                }
            } else {
                setLensCorrectionMode(PiLensCorrectionMode.FISH_EYE);
            }
            sSkipFrameWithPreview.setCheck(false);

            mPiPano.useGyroscope(!isStitched, file.getAbsolutePath());
            if (!isStitched) {
                mPiPano.setParamByMedia(file.getAbsolutePath());
                mPiPano.setParamReCaliEnable(1, false);
            }

            // 预读照片尺寸
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            final int bitmapWidth = options.outWidth;
            Log.d(TAG, "load,bitmapWidth:" + bitmapWidth);
            if (bitmapWidth <= 720) {
                requestDrawBitmap(decodeFile(1));
            } else {
                // first load
                int sampleSize = bitmapWidth / (bitmapWidth < PREVIEW_SIZE_MAX_WIDTH ? 1920 : 720);
                Log.d(TAG, "load,1.sampleSize:" + sampleSize);
                requestDrawBitmap(decodeFile(sampleSize));
                // final load
                if (bitmapWidth > PREVIEW_SIZE_MAX_WIDTH) {
                    sampleSize = (int) Math.ceil(bitmapWidth * 1f / PREVIEW_SIZE_MAX_WIDTH);
                    Log.d(TAG, "load,2.sampleSize:" + sampleSize);
                    requestDrawBitmap(decodeFile(sampleSize));
                }
            }
            if (!isQuit && null != listener) {
                listener.onPlayCompleted(file);
            }
        }

        private Bitmap decodeFile(int inSampleSize) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = inSampleSize;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }

        private void requestDrawBitmap(Bitmap bitmap) {
            if (isQuit) {
                return;
            }
            drawBitmap(bitmap);
        }

        @Override
        public void play() {
            if (!isQuit) {
                Log.d(TAG, "play");
                mHandler.removeCallbacks(this);
                mHandler.post(this);
            }
        }

        @Override
        public void release() {
            if (!isQuit) {
                Log.d(TAG, "release...");
                isQuit = true;
                mHandler.removeCallbacks(this);
                mHandler.getLooper().quit();
                releaseGlSurface();
                resetParamReCaliEnable();
                try {
                    mThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "release,end");
            }
        }
    }

    /**
     * 视频播状态
     */
    @IntDef({
            VideoPlayState.release,
            VideoPlayState.prepared,
            VideoPlayState.started,
            VideoPlayState.paused,
            VideoPlayState.completed
    })
    public @interface VideoPlayState {
        /**
         * 释放或未初始化
         */
        int release = -1;
        /**
         * 准备
         */
        int prepared = 0;
        /**
         * 开始
         */
        int started = 1;
        /**
         * 暂停
         */
        int paused = 2;
        /**
         * 完成
         */
        int completed = 3;
    }

    public interface VideoPlayListener {
        /**
         * 初始化结束。
         * 初始化成功后才可使用 playControl 播放。
         *
         * @param ret 0：成功；非0：失败
         */
        void onPlayControlInit(IVideoPlayControl playControl, int ret);

        /**
         * 释放。
         * 释放后 playControl 不可再使用。
         */
        void onPlayControlRelease(IVideoPlayControl playControl);

        /**
         * 播放状态变化
         *
         * @param state 当前的播放状态
         */
        void onPlayStateChange(IVideoPlayControl playControl, int state);

        /**
         * 播放时间更新
         *
         * @param currentTime   当前视频播放时间
         * @param videoDuration 视频总长度
         */
        void onPlayTimeChange(IVideoPlayControl playControl, float currentTime, float videoDuration);
    }

    public interface IVideoPlayControl extends IPlayControl {
        @VideoPlayState
        int getState();

        void pause();

        /**
         * 调整进度
         *
         * @param progress 百分比进度
         */
        void seek(float progress);

        /**
         * 播放总时长，单位毫秒
         */
        float getDuration();

        /**
         * 当前播放的时间点，单位毫秒
         */
        float getCurTime();
    }

    /**
     * @param file       待播放的文件
     * @param isStitched 是否拼接
     * @return null:时不可播放
     */
    public IVideoPlayControl playVideo(File file, boolean isStitched, VideoPlayListener listener) {
        Log.d(TAG, "playVideo," + file + "," + mPlay);
        if (null != mPlay) {
            mPlay.release();
            mPlay = null;
        }
        // 验证是否是未拼接的文件
        if (isStitched) {
            mPlay = new StitchVideoPlayControl(file, listener);
        } else {
            mPlay = new UnStitchVideoMultiPlayControl(file, listener);
        }
        return (IVideoPlayControl) mPlay;
    }

    /**
     * 拼接视频播放。
     * 直接使用系统MediaPlayer解析。
     */
    protected class StitchVideoPlayControl implements IVideoPlayControl {
        private final String TAG = MediaPlayerSurfaceView.TAG + "$StitchVideo";
        private final String filepath;
        private final VideoPlayListener listener;
        private final Handler mHandler;
        @VideoPlayState
        private volatile int state = VideoPlayState.release;
        private MediaPlayer mMyMediaPlayer;
        private final HandlerThread mThread;

        protected StitchVideoPlayControl(File file, @Nullable VideoPlayListener listener) {
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
            if (state == VideoPlayState.started) {
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
            waitPanoInit(2000);
            if (null == mPiPano || !mPiPano.mHasInit) {
                Log.e(TAG, "initPlayer,PiPano not init.");
                if (null != listener) {
                    listener.onPlayControlInit(this, 1);
                }
                return;
            }
            sSkipFrameWithPreview.setCheck(false);

            mPiPano.setParamReCaliEnable(0, false); // 拼接视频不需要拼接距离
            setLensCorrectionMode(PiLensCorrectionMode.STITCHED_2);
            mMyMediaPlayer = new MediaPlayer();
            try {
                releaseGlSurface();
                mMyMediaPlayer.setSurface(obtainGlSurface(0));
                mMyMediaPlayer.reset();
                mMyMediaPlayer.setDataSource(filepath);
                mMyMediaPlayer.prepare();
                mMyMediaPlayer.seekTo(0); // seek to start,show first frame.
            } catch (Exception ex) {
                Log.e(TAG, "initPlayer,ex:" + ex);
                ex.printStackTrace();
                if (null != listener) {
                    listener.onPlayControlInit(this, 1);
                }
                releasePlayer();
                return;
            }
            mMyMediaPlayer.setOnSeekCompleteListener(mp -> {
                if (state == VideoPlayState.started) {
                    checkState();
                }
            });
            mMyMediaPlayer.setOnCompletionListener(mp -> changeState(VideoPlayState.completed));
            if (null != listener) {
                listener.onPlayControlInit(this, 0);
            }
            changeState(VideoPlayState.prepared);
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
                releaseGlSurface();
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
            if (state != VideoPlayState.started) {
                Log.d(TAG, "play");
                changeState(VideoPlayState.started);
                mHandler.post(this::checkState);
            }
        }

        @Override
        public void pause() {
            if (state != VideoPlayState.paused) {
                Log.d(TAG, "paused");
                changeState(VideoPlayState.paused);
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
            if (state != VideoPlayState.release) {
                Log.d(TAG, "release...");
                mHandler.removeCallbacksAndMessages(null);
                changeState(VideoPlayState.release);
                mHandler.post(this::checkState);
                resetParamReCaliEnable();
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
            return null != mMyMediaPlayer && state != VideoPlayState.release ?
                    mMyMediaPlayer.getDuration() : 0;
        }

        @Override
        public float getCurTime() {
            return null != mMyMediaPlayer && state != VideoPlayState.release ?
                    mMyMediaPlayer.getCurrentPosition() : 0;
        }

        private void checkState() {
            if (null == mMyMediaPlayer) {
                return;
            }
            Log.d(TAG, "checkState,state:" + state);
            switch (state) {
                case VideoPlayState.started: {
                    if (!mMyMediaPlayer.isPlaying()) {
                        mMyMediaPlayer.start();
                        loopTick();
                    }
                }
                break;
                case VideoPlayState.paused: {
                    if (mMyMediaPlayer.isPlaying()) {
                        mMyMediaPlayer.pause();
                    }
                }
                break;
                case VideoPlayState.release: {
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
            keepScreenOn(VideoPlayState.started == state);
        }

        private void notifyStateTimeChange() {
            if (listener != null) {
                listener.onPlayTimeChange(this, getCurTime(), getDuration());
            }
        }
    }

    protected class UnStitchVideoMultiPlayControl implements IVideoPlayControl {
        private final String TAG = "UnStitchVideoMultiPlayControl";
        private final String filepath;
        private final VideoPlayListener listener;
        private final Handler mHandler;
        private boolean callReleased;
        @VideoPlayState
        private volatile int mState = VideoPlayState.release;

        private VideoThread mVideoThread;

        private AudioDecoder mAudioDecoder;
        private float mDuration;

        private final HandlerThread mThread;
        private final Object mAudioLock = new Object();

        protected UnStitchVideoMultiPlayControl(File file, @Nullable VideoPlayListener listener) {
            this.filepath = file.getAbsolutePath();
            this.listener = listener;
            mThread = new HandlerThread("play:" + file.getName());
            mThread.start();
            mHandler = new Handler(mThread.getLooper());
            mHandler.post(this::initPlayer);
        }

        private final Runnable mTickRunnable = () -> {
            if (null != mVideoThread && mState == VideoPlayState.started) {
                notifyStateTimeChange();
            }
            if (mState == VideoPlayState.started) {
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
            waitPanoInit(2000);
            if (null == mPiPano || !mPiPano.mHasInit || callReleased) {
                Log.e(TAG, "initPlayer,PiPano not init.");
                notifyPlayControlInit(1);
                return;
            }
            File mp4File0 = new File(filepath, "0.mp4");
            if (!mp4File0.exists()) {
                Log.e(TAG, "initPlayer, can not find 0.mp4 !");
                notifyPlayControlInit(1);
                return;
            }

            mDuration = StitchingUtil.getDuring(mp4File0.getAbsolutePath()) / 1000.0f;

            try {
                mPiPano.pausePreviewUpdate();
                setLensCorrectionMode(PiLensCorrectionMode.PANO_MODE_4);
                mPiPano.useGyroscope(
                        new File(filepath, "stabilization").exists(),
                        filepath);
                releaseGlSurface();
                mPiPano.setParamReCaliEnable(60, false);
                mPiPano.resumePreviewUpdate();
            } catch (Exception ex) {
                Log.e(TAG, "initPlayer, video ex:" + ex);
                ex.printStackTrace();
                if (mPiPano != null) {
                    mPiPano.resumePreviewUpdate();
                }
                notifyPlayControlInit(1);
                return;
            }
            try {
                mAudioDecoder = new AudioDecoder(mp4File0.getAbsolutePath());
            } catch (Exception ex) {
                Log.e(TAG, "initPlayer, audio ex:" + ex);
                ex.printStackTrace();
            }
            notifyPlayControlInit(0);
            mState = VideoPlayState.prepared;
            notifyStateChange();
            notifyStateTimeChange();
            Log.d(TAG, "initPlayer,ok");
            if (mPiPano != null) {
                mVideoThread = new VideoThread(filepath, mPiPano);
                mVideoThread.start();
            }
        }

        private void releasePlayer() {
            Log.d(TAG, "releasePlayer...");
            if (null != mVideoThread) {
                try {
                    mVideoThread.join();
                } catch (InterruptedException ex) {
                    Log.e(TAG, "releasePlayer 1, video ex:" + ex);
                    ex.printStackTrace();
                }
                mVideoThread = null;
            }
            Log.d(TAG, "releasePlayer...mAudioThread ");
            synchronized (mAudioLock) {
                mAudioLock.notifyAll();
            }
            if (null != mAudioThread) {
                try {
                    mAudioThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mAudioThread = null;
            }
            Log.d(TAG, "releasePlayer...mAudioDecoder ");
            if (null != mAudioDecoder) {
                mAudioDecoder.exitAudio();
                mAudioDecoder = null;
            }
            releaseGlSurface();
            Log.d(TAG, "releasePlayer,ok");
        }

        @Override
        public int getState() {
            return mState;
        }

        private Thread mAudioThread;

        @Override
        public void play() {
            if (mState != VideoPlayState.started) {
                Log.d(TAG, "play :" + mState);
                mState = VideoPlayState.started;
                notifyStateChange();
                //seek(0);
                loopTick();
                if (null != mAudioDecoder) {
                    if (mAudioThread != null && mAudioDecoder.mAudioTrack != null) {
                        synchronized (mAudioLock) {
                            mAudioLock.notifyAll();
                        }
                        return;
                    }
                    mAudioThread = new Thread(mAudioDecoder, "AudioDecoder");
                    mAudioThread.start();
                }
            }
        }

        @Override
        public void pause() {
            if (mState != VideoPlayState.paused) {
                Log.d(TAG, "paused");
                mState = VideoPlayState.paused;
                notifyStateChange();
            }
        }

        @Override
        public void seek(float progress) {
            long timeUs = (long) (mDuration * 1000L * progress);
            Log.d(TAG, "seek," + progress + ",mDuration:" + mDuration + ",timeMs:" + timeUs / 1000);
            if (null != mVideoThread) {
                mVideoThread.seek(timeUs);
            }
            if (null != mAudioDecoder) {
                mAudioDecoder.seek(timeUs);
            }
            if (mState == VideoPlayState.paused) {
                play();
            }
        }

        @Override
        public void release() {
            boolean needRelease = mState != VideoPlayState.release;
            Log.d(TAG, "release " + (needRelease ? "start" : "filter") + ",state: " + mState);
            if (needRelease) {
                mHandler.removeCallbacksAndMessages(null);
                mState = VideoPlayState.release;
                releasePlayer();
                mHandler.getLooper().quitSafely();
                resetParamReCaliEnable();
                try {
                    mThread.join();
                } catch (InterruptedException ex) {
                    Log.d(TAG, "release,ex:" + ex);
                    ex.printStackTrace();
                }
                notifyStateChange();
                notifyPlayControlRelease();
                Log.d(TAG, "release,end");
            }
            callReleased = true;
        }

        @Override
        public float getDuration() {
            return mDuration;
        }

        @Override
        public float getCurTime() {
            if (null != mVideoThread) {
                if (mState == VideoPlayState.started) {
                    return mVideoThread.getSampleTime() / 1000f;
                }
            }
            return 0;
        }

        private void notifyPlayControlInit(int ret) {
            if (null != listener) {
                listener.onPlayControlInit(this, ret);
            }
        }

        private void notifyPlayControlRelease() {
            if (null != listener) {
                listener.onPlayControlRelease(this);
            }
        }

        private void notifyStateChange() {
            if (listener != null) {
                listener.onPlayStateChange(this, mState);
            }
            keepScreenOn(VideoPlayState.started == mState);
        }

        private void notifyStateTimeChange() {
            if (listener != null) {
                float curTime = getCurTime();
                float duration = getDuration();
                listener.onPlayTimeChange(this, curTime, duration);
            }
        }

        private class VideoThread extends Thread {
            private final StitchingNative mStitchingNative;
            private long mSeekToTimeUs = -1;
            private final PlayerTimeHelper mPlayerTimeHelper = new PlayerTimeHelper();
            boolean resetPauseTimeBySeek = false;

            VideoThread(String filepath, PiPano piPano) {
                mStitchingNative = new StitchingNative();
                mStitchingNative.startStitching(filepath, piPano, true);
            }

            @Override
            public void run() {
                long realCostTimeMs;
                long diffTimeMs;
                long presentationTimeMs = 0;
                if (mPiPano == null) {
                    return;
                }
                mPiPano.mFrameAvailableTask[0].mCameraFrameCount = 0;
                sSkipFrameWithPreview.reset();
                mStitchingNative.seekToPreviousKeyFrame(0);
                while (mState != VideoPlayState.release) {
                    if (mSeekToTimeUs >= 0) {
                        mStitchingNative.seekToPreviousKeyFrame(mSeekToTimeUs);
                        resetPauseTimeBySeek = true;
                        mPlayerTimeHelper.setLastPausedVideoTimeMs(mSeekToTimeUs / 1000);
                        mSeekToTimeUs = -1;
                    }
                    boolean startPlay = mState == VideoPlayState.started;
                    if (startPlay || sSkipFrameWithPreview.check) {
                        if (sSkipFrameWithPreview.check) {
                            sSkipFrameWithPreview.skipFrame--;
                        }
                        if (startPlay && mPlayerTimeHelper.getStartTimeMs() == 0) {
                            mPlayerTimeHelper.setStartTimeMs(System.currentTimeMillis());
                            Log.d(TAG, "video reset startTimeMs :" + mPlayerTimeHelper);
                        }
                        if (!mStitchingNative.extractorOneFrame()) {
                            seek(1);
                            mState = VideoPlayState.completed;
                            notifyStateChange();
                            Log.i(TAG, "VideoThread has no more frame");
                        }
                        mStitchingNative.decodeOneFrame(true);
                        if (startPlay) {
                            presentationTimeMs = mStitchingNative.getExtractorSampleTime(0) / 1000;
                            if (resetPauseTimeBySeek) {
                                resetPauseTimeBySeek = false;
                                mPlayerTimeHelper.setLastPausedVideoTimeMs(presentationTimeMs);
                            }
                            realCostTimeMs = mPlayerTimeHelper.calculateCostTime();
                            diffTimeMs = realCostTimeMs - presentationTimeMs;
//                            Log.d(TAG, "handleVideoOutputBuffer pre:" + presentationTimeMs
//                                    + ",cost: " + realCostTimeMs
//                                    + ",diff," + diffTimeMs + "," + mPlayerTimeHelper);
                            if (diffTimeMs < 0) {
                                SystemClock.sleep(-diffTimeMs);
                            }
                            mPlayerTimeHelper.setCurPlayerTime(presentationTimeMs);
                        }
                    }
                    if (mState == VideoPlayState.completed || mState == VideoPlayState.paused) {
                        mPlayerTimeHelper.setStartTimeMs(0);
                        if (mState == VideoPlayState.paused) {
                            mPlayerTimeHelper.setLastPausedVideoTimeMs(presentationTimeMs);
                        }
                    }
                }
                mStitchingNative.deleteFrameExtractor();
            }

            public void seek(long timeUs) {
                mSeekToTimeUs = timeUs;
            }

            public float getSampleTime() {
                return mStitchingNative.getExtractorSampleTime(0);
            }
        }

        private class AudioDecoder implements Runnable {
            private static final long ONE_SECONDS = 1_000_000;
            private static final int TIME_OUT = 10_000;
            private final String filepath0;
            private MediaExtractor mMediaExtractor = null;
            private MediaCodec mMediaCodec = null;
            private AudioTrack mAudioTrack = null;
            MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

            private volatile long seekTimeUs = -1;
            private int mChannelCount = 2;
            private byte[] inputBytes, outputBytes;
            private final PlayerTimeHelper mPlayerTimeHelper = new PlayerTimeHelper();
            long presentationTimeMs = 0;
            long realCostTimeMs;
            long diffTimeMs;
            private boolean isNeedAudioWait = false;

            public AudioDecoder(String filepath0) throws IOException {
                this.filepath0 = filepath0;
                initAudio(filepath0);
            }

            private void initAudio(String filepath0) throws IOException {
                try {
                    mMediaExtractor = new MediaExtractor();
                    mMediaExtractor.setDataSource(filepath0);
                    MediaFormat audioFormat = null;
                    boolean audio = false;
                    int fps = 30;
                    for (int trackIndex = 0, numTracks = mMediaExtractor.getTrackCount(); trackIndex < numTracks; trackIndex++) {
                        MediaFormat format = mMediaExtractor.getTrackFormat(trackIndex);
                        String mime = format.getString(MediaFormat.KEY_MIME);
                        if (mime.startsWith("audio/") && !audio) {
                            audioFormat = format;
                            mMediaExtractor.selectTrack(trackIndex);
                            audio = true;
                        } else if (mime.startsWith("video/")) {
                            fps = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                        }
                    }
                    if (null == audioFormat) {
                        throw new RuntimeException("No audio track found in " + filepath0);
                    }
                    Log.d(TAG, "initAudio fps :" + fps);
                    isNeedAudioWait = fps <= 15;
                    final String mime = audioFormat.getString(MediaFormat.KEY_MIME);
                    //
                    int audioChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    mChannelCount = audioChannels;
                    mMediaCodec = MediaCodec.createDecoderByType(mime);
                    mMediaCodec.configure(audioFormat, null, null, 0);
                    mMediaCodec.start();
                    int audioSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    final int min_buf_size = AudioTrack.getMinBufferSize(audioSampleRate,
                            (audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                            AudioFormat.ENCODING_PCM_16BIT);
                    final int max_input_size = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    int audioInputBufSize = Math.max(min_buf_size * 4, max_input_size);
                    final int frameSizeInBytes = audioChannels * 2;
                    audioInputBufSize = (audioInputBufSize / frameSizeInBytes) * frameSizeInBytes;
                    mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, audioSampleRate,
                            (audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                            AudioFormat.ENCODING_PCM_16BIT, audioInputBufSize, AudioTrack.MODE_STREAM);
                    mAudioTrack.play();
                } catch (IOException ex) {
                    if (mMediaCodec != null) {
                        mMediaCodec.reset();
                        mMediaCodec.release();
                        mMediaCodec = null;
                    }
                    if (null != mMediaExtractor) {
                        mMediaExtractor.release();
                        mMediaExtractor = null;
                    }
                    if (mAudioTrack != null) {
                        mAudioTrack.release();
                        mAudioTrack = null;
                    }
                    throw ex;
                }
            }

            private boolean decodeOneAudioFrame() {
                if (seekTimeUs >= 0) {
                    long realSeekTime = ((int) (seekTimeUs / ONE_SECONDS)) * ONE_SECONDS;
                    Log.d(TAG, "audio seek " + seekTimeUs + ",real =>" + realSeekTime + "," + mPlayerTimeHelper);
                    mMediaExtractor.seekTo(realSeekTime, realSeekTime > 0 ?
                            MediaExtractor.SEEK_TO_PREVIOUS_SYNC : MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    mPlayerTimeHelper.setLastPausedVideoTimeMs(realSeekTime / 1000);
                    seekTimeUs = -1;
                    mMediaCodec.flush();
                }
                handleAudioInputBuffer();
                handleAudioOutputBuffer();
                return (mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            }


            private int handleAudioInputBuffer() {
                int inputIndex = mMediaCodec.dequeueInputBuffer(TIME_OUT);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputIndex);
                    int sampleSize = mMediaExtractor.readSampleData(inputBuffer, 0);
                    if (sampleSize > 0) {
                        mMediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, mMediaExtractor.getSampleTime(), 0);
                        mMediaExtractor.advance();
                    } else {
                        mMediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }
                return inputIndex;
            }

            private int handleAudioOutputBuffer() {
                int outIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIME_OUT);
                if (outIndex > 0) {
                    ByteBuffer byteBuffer = mMediaCodec.getOutputBuffer(outIndex);
                    if (mChannelCount == 4) {
                        checkByteBuffer(byteBuffer);
                        byteBuffer.get(inputBytes);
                        changeToTwoChannelAudio(inputBytes, outputBytes, AudioFormat.ENCODING_PCM_16BIT);
                        byteBuffer = ByteBuffer.wrap(outputBytes);
                    }
                    presentationTimeMs = mBufferInfo.presentationTimeUs / 1000;
                    mAudioTrack.write(byteBuffer, byteBuffer.remaining(), AudioTrack.WRITE_BLOCKING, mBufferInfo.presentationTimeUs);
                    mMediaCodec.releaseOutputBuffer(outIndex, false);
                    realCostTimeMs = mPlayerTimeHelper.calculateCostTime();
                    diffTimeMs = realCostTimeMs - presentationTimeMs;
//                    Log.d(TAG, "audio time ==> cur:" + getCurTime() +
//                            ",pre: " + presentationTimeMs
//                            + ", cost :" + realCostTimeMs + ",diff," + diffTimeMs + "," + mPlayerTimeHelper);
                    if (diffTimeMs < 0 && isNeedAudioWait) {
                        SystemClock.sleep(-diffTimeMs);
                    }
                    mPlayerTimeHelper.setCurPlayerTime(presentationTimeMs);
                }
                return outIndex;
            }

            /**
             * 从 pano 4声道数据中 取出 左右声道数据
             */
            private void changeToTwoChannelAudio(byte[] inputBytes, byte[] outputBytes, int audioEncoding) {
                if (audioEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                    return;
                }
                // pano 4声道数据分布 []{ front, left ,right ,back }
                int channelLeft = 1, channelRight = 2;
                int j = 0;
                final int channelCount = 4;
                // audioEncoding(pcm_16bit) 占字节个数
                final int oneChannelByte = 2;
                int step = channelCount * oneChannelByte;
                // 取出 左右声道数据
                int length = inputBytes.length;
                for (int i = 0; i < length; i = i + step) {
                    outputBytes[j++] = inputBytes[i + channelLeft * oneChannelByte];
                    outputBytes[j++] = inputBytes[i + channelLeft * oneChannelByte + 1];

                    outputBytes[j++] = inputBytes[i + channelRight * oneChannelByte];
                    outputBytes[j++] = inputBytes[i + channelRight * oneChannelByte + 1];
                }
            }

            @Override
            public void run() {
                Log.i(TAG, "audio run start =====>" + this);
                mBufferInfo = new MediaCodec.BufferInfo();
                if (mMediaExtractor == null) {
                    try {
                        initAudio(filepath0);
                    } catch (Exception ex) {
                        Log.e(TAG, "run,ex:" + ex);
                        return;
                    }
                }
                boolean end = false;
                while (mState != VideoPlayState.release && !end) {
                    while (mState == VideoPlayState.started) {
                        try {
                            if (mPlayerTimeHelper.getStartTimeMs() == 0) {
                                mPlayerTimeHelper.setStartTimeMs(System.currentTimeMillis());
                            }
                            end = decodeOneAudioFrame();
                        } catch (Exception ex) {
                            Log.e(TAG, "run,decodeOneAudioFrame happen ex:" + ex);
                        }
                    }
                    Log.d(TAG, "run,play audio end state," + mState + ",,end :" + end);
                    if (end) {
                        seek(1);
                    }
                    mAudioTrack.pause();
                    mAudioTrack.flush();
                    if (mState == VideoPlayState.paused) {
                        mPlayerTimeHelper.setStartTimeMs(0);
                        mPlayerTimeHelper.setLastPausedVideoTimeMs(presentationTimeMs);
                        synchronized (mAudioLock) {
                            try {
                                Log.d(TAG, "mAudioLock wait " + mState + ",," + end);
                                mAudioLock.wait();
                                Log.d(TAG, "mAudioLock wait end" + mState + ",," + end);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (mState == VideoPlayState.started) {
                            mPlayerTimeHelper.setStartTimeMs(0);
                            mBufferInfo = new MediaCodec.BufferInfo();
                            mAudioTrack.play();
                            end = false;
                        }
                    }
                    if (mState == VideoPlayState.completed) {
                        Log.d(TAG, "audio decode break by completed");
                        mPlayerTimeHelper.setStartTimeMs(0);
                        break;
                    }
                }
                if (mState == VideoPlayState.release || mState == VideoPlayState.completed) {
                    exitAudio();
                    mPlayerTimeHelper.setStartTimeMs(0);
                }
                Log.i(TAG, "audio run end =====>" + mState + "," + this);
            }

            public void seek(long timeUs) {
                seekTimeUs = timeUs;
            }

            public void exitAudio() {
                Log.d(TAG, "exitAudio");
                if (mAudioTrack != null) {
                    mAudioTrack.stop();
                    mAudioTrack.release();
                    mAudioTrack = null;
                }
                if (mMediaCodec != null) {
                    mMediaCodec.reset();
                    mMediaCodec.release();
                    mMediaCodec = null;
                }
                if (null != mMediaExtractor) {
                    mMediaExtractor.release();
                    mMediaExtractor = null;
                }
                inputBytes = null;
                outputBytes = null;
            }

            private void checkByteBuffer(ByteBuffer buffer) {
                if (inputBytes == null) {
                    inputBytes = new byte[buffer.remaining()];
                }
                if (inputBytes.length != buffer.remaining()) {
                    inputBytes = new byte[buffer.remaining()];
                }
                if (outputBytes == null) {
                    outputBytes = new byte[inputBytes.length / 2];
                }
                if (outputBytes.length != inputBytes.length / 2) {
                    outputBytes = new byte[inputBytes.length / 2];
                }
            }
        }
    }

    private void keepScreenOn(boolean keepScreenOn) {
        if (Looper.getMainLooper().isCurrentThread()) {
            setKeepScreenOn(keepScreenOn);
        } else {
            post(() -> setKeepScreenOn(keepScreenOn));
        }
    }

    private static final class PlayerTimeHelper {
        private volatile long startTimeMs = 0L;
        private volatile long lastPausedVideoTimeMs = 0L;
        private volatile long curVideoTime = -1;

        public long getStartTimeMs() {
            return startTimeMs;
        }

        public void setStartTimeMs(long startTimeMs) {
            this.startTimeMs = startTimeMs;
            if (startTimeMs == 0) {
                curVideoTime = -1;
            }
        }

        public void setLastPausedVideoTimeMs(long lastPausedVideoTimeMs) {
            this.lastPausedVideoTimeMs = lastPausedVideoTimeMs;
        }

        public void setCurPlayerTime(long curVideoTime) {
//        Log.d(TAG, "setCurPlayerTime ==>" + curVideoTime + ",," + this.curVideoTime);
            this.curVideoTime = curVideoTime;
        }

        public long calculateCostTime() {
            return System.currentTimeMillis() - startTimeMs + lastPausedVideoTimeMs;
        }

        @Override
        public String toString() {
            return "SyncTime{" +
                    "startMs=" + startTimeMs +
                    ", lastPausedMs=" + lastPausedVideoTimeMs +
                    ", curVideMs=" + curVideoTime +
                    '}';
        }
    }

    private static class SkipFrame {
        int skipFrame;
        int skipDrawFrame;
        private boolean check;

        public SkipFrame(int skipFrame, int skipDrawFrame) {
            this.skipFrame = skipFrame;
            this.skipDrawFrame = skipDrawFrame;
            check = true;
        }

        boolean filter() {
            return skipFrame > 0 && skipDrawFrame > 0 && check;
        }

        public void setCheck(boolean check) {
            this.check = check;
        }

        public void reset() {
            this.skipFrame = MAX_IGNORE_DECODE_FRAME_NUM;
            this.skipDrawFrame = MAX_IGNORE_DRAW_FRAME_NUM;
            check = true;
        }
    }
}
