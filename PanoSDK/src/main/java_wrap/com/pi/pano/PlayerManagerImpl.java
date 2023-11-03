package com.pi.pano;

import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.pi.pano.wrap.VideoPlayerView;

import java.io.File;

public class PlayerManagerImpl implements IPlayerManager {

    private static final String TAG = "PlayerManager";

    private SurfaceView mCurSurfaceView;
    private ViewGroup mParentView;
    /**
     * 使用 保护镜
     */
    private boolean mLensProtected;
    /**
     * 使用陀螺仪
     */
    private boolean mIsOpenGyroscope;
    private int flag;

    private MediaPlayerSurfaceView.IPlayControl mPlayControl;

    private final PanoSurfaceViewListener mPanoSurfaceViewListener = new PanoSurfaceViewListener() {

        @Override
        public void onPanoSurfaceViewCreate() {
            if (mCurSurfaceView instanceof MediaPlayerSurfaceView) {
                ((MediaPlayerSurfaceView) mCurSurfaceView).setLensProtected(mLensProtected);
            }
        }

        @Override
        public void onPanoSurfaceViewRelease() {
            mCurSurfaceView = null;
            mParentView = null;
        }

        @Override
        public void onPanoModeChange(int mode) {

        }

        @Override
        public void onSingleTapConfirmed() {
            if (mParentView != null) {
                mParentView.performClick();
            }
        }

        @Override
        public void onEncodeFrame(int count) {

        }
    };
    //region public

    @Override
    public MediaPlayerSurfaceView.IPlayControl playImage(@NonNull ViewGroup parent, @NonNull File file, @NonNull MediaPlayerSurfaceView.ImagePlayListener listener) {
        return playImage(parent, file, false, listener);
    }

    @Override
    public MediaPlayerSurfaceView.IPlayControl playImage(@NonNull ViewGroup parent, @NonNull File file, boolean lensProtected, @NonNull MediaPlayerSurfaceView.ImagePlayListener listener) {
        setLensProtected(lensProtected);
        String name = file.getName();
        boolean stitch = name.endsWith("_s.jpg") || name.endsWith("_s_hdr.jpg");
        MediaPlayerSurfaceView.IPlayControl iPlayControl = switchPanoSurface(parent, 1).playImage(file, stitch, listener);
        mPlayControl = iPlayControl;
        return iPlayControl;
    }

    @Override
    public MediaPlayerSurfaceView.IVideoPlayControl playVideo(@NonNull ViewGroup parent, @NonNull File file, @NonNull MediaPlayerSurfaceView.VideoPlayListener listener) {
        return playVideo(parent, file, false, null, listener);
    }

    @Override
    public MediaPlayerSurfaceView.IVideoPlayControl playVideo(@NonNull ViewGroup parent, @NonNull File file, boolean lensProtected,
                                                              Size size, @NonNull MediaPlayerSurfaceView.VideoPlayListener listener) {
        setLensProtected(lensProtected);
        if (file.isFile()) {
            int[] ret = VideoPlayerView.obtainVideoSize(file);
            if (null != ret) {
                boolean isPanoVideo = VideoPlayerView.isPanoVideo(ret);
                if (!isPanoVideo) {
                    VideoPlayerView playerView = switchPlaneView(parent);
                    if (size == null || size.getWidth() == 0 || size.getHeight() == 0) {
                        size = new Size(parent.getWidth(), parent.getHeight());
                    }
                    if (size.getWidth() == 0 || size.getHeight() == 0) {
                        DisplayMetrics displayMetrics = parent.getContext().getResources().getDisplayMetrics();
                        size = new Size(displayMetrics.widthPixels, displayMetrics.heightPixels);
                        Log.w(TAG, "changeSurfaceSize width height is 0 ,re init ! " + size);
                    }
                    changeSurfaceSize(playerView, ret[0], ret[1], size);
                    MediaPlayerSurfaceView.IVideoPlayControl control = playerView.playVideo(file, listener);
                    mPlayControl = control;
                    return control;
                }
            }
        }
        int cameraCount = 2;
        String name = file.getName();
        if (file.isFile() && name.endsWith("_s.mp4")) {
            cameraCount = 1;
        } else {
            File child = new File(file, "0.mp4");
            int[] ret = VideoPlayerView.obtainVideoSize(child);
            if (null != ret) {
                boolean isPanoVideo = VideoPlayerView.isPanoVideo(ret);
                cameraCount = isPanoVideo ? 1 : 2;
            }
        }
        MediaPlayerSurfaceView.IVideoPlayControl control = switchPanoSurface(parent, cameraCount).playVideo(file, lensProtected, listener);
        mPlayControl = control;
        return control;
    }

    @Override
    public void release() {
        if (mPlayControl != null) {
            mPlayControl.release();
        }
        mCurSurfaceView = null;
        mParentView = null;
        flag = 0;
    }


    @Override
    public void openGyroscope(boolean open) {
        mIsOpenGyroscope = open;
        if (mCurSurfaceView instanceof MediaPlayerSurfaceView) {
            ((MediaPlayerSurfaceView) mCurSurfaceView).useGyroscope(open);
        }
    }

    //endregion

    @Override
    public SurfaceView getPlayerView() {
        return mCurSurfaceView;
    }

    //region private

    private MediaPlayerSurfaceView switchPanoSurface(ViewGroup parent, int cameraCount) {
        if (mCurSurfaceView != null) {
            if (mCurSurfaceView instanceof MediaPlayerSurfaceView) {
                ((MediaPlayerSurfaceView) mCurSurfaceView).setOnPanoModeChangeListener(null);
            }
            parent.removeView(mCurSurfaceView);
            mCurSurfaceView = null;
            this.flag = -1;
        }
        MediaPlayerSurfaceView surfaceView = new MediaPlayerSurfaceView(parent.getContext(), cameraCount);
        surfaceView.setOnPanoModeChangeListener(mPanoSurfaceViewListener);
        surfaceView.useGyroscopeForPlayer(mIsOpenGyroscope);
        setCurView(parent, 0, surfaceView);
        mParentView = parent;
        return surfaceView;
    }

    private VideoPlayerView switchPlaneView(ViewGroup parent) {
        if ((mCurSurfaceView instanceof VideoPlayerView) && this.flag == 1) {
            return (VideoPlayerView) mCurSurfaceView;
        }
        if (mCurSurfaceView != null) {
            parent.removeView(mCurSurfaceView);
            mCurSurfaceView = null;
            this.flag = -1;
        }
        VideoPlayerView surfaceView = new VideoPlayerView(parent.getContext());
        setCurView(parent, 1, surfaceView);
        mParentView = parent;
        return surfaceView;
    }

    private void changeSurfaceSize(View view, int videoWidth, int videoHeight, Size size) {
        int[] size2 = SurfaceSizeUtils.calculateSurfaceSize(size.getWidth(), size.getHeight(), videoWidth, videoHeight);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        layoutParams.width = size2[0];
        layoutParams.height = size2[1];
        layoutParams.gravity = Gravity.CENTER;
        view.setLayoutParams(layoutParams);
        Log.d(TAG, "changeSurfaceSize end :" + size2[0] + "*" + size2[1]);
    }

    private void setCurView(ViewGroup parent, int flag, SurfaceView surfaceView) {
        this.flag = flag;
        this.mCurSurfaceView = surfaceView;
        parent.addView(surfaceView);
    }

    private void setLensProtected(boolean lensProtected) {
        mLensProtected = lensProtected;
    }

    //endregion
}
