package com.pi.pano;

import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiCameraBehavior;
import com.pi.pano.annotation.PiLensCorrectionMode;
import com.pi.pano.annotation.PiPreviewMode;
import com.pi.pano.error.PiErrorCode;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class DefaultChangeResolutionListener extends ChangeResolutionListener {
    protected final String TAG = DefaultChangeResolutionListener.class.getSimpleName();
    // （请求的）视角
    protected final int fieldOfView;
    // 画面比例
    @NonNull
    protected final String aspectRatio;
    // 是否全景
    protected final boolean isPanorama;

    static final ExecutorService sExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "ChangeResolution" + mCount.incrementAndGet());
        }
    });

    public DefaultChangeResolutionListener() {
        this(90, "2:1");
    }

    public DefaultChangeResolutionListener(@NonNull String aspectRatio) {
        this(90, aspectRatio);
    }

    public DefaultChangeResolutionListener(int fieldOfView, @NonNull String aspectRatio) {
        if (aspectRatio.split(":").length != 2) {
            throw new RuntimeException("aspectRatio is error:" + aspectRatio);
        }
        this.fieldOfView = fieldOfView;
        this.aspectRatio = aspectRatio;
        this.isPanorama = "2:1".equals(aspectRatio);
        PilotSDK.setPreviewImageReaderFormat(null);
    }

    @Override
    public void fillParams(@Nullable String cameraId, int width, int height, int fps) {
        super.fillParams(cameraId, width, height, fps);
        PilotSDK.setLockDefaultPreviewFps(isLockDefaultPreviewFps());
    }

    protected void setPreviewParam() {
        PilotSDK.setParamReCaliEnable(PilotSDK.getFps() * 2, false);
        PilotSDK.setLensCorrectionMode(PiLensCorrectionMode.PANO_MODE_2);
        PilotSDK.setPreviewMode(PiPreviewMode.planet, 180, false, fieldOfView, 0);
        initStabilizationTimeOffset();
    }

    @Override
    protected void onSuccess(@PiCameraBehavior int behavior) {
        super.onSuccess(behavior);
        Log.d(TAG, "ChangeResolution[" + mWidth + "*" + mHeight + "] ,onSuccess :" + behavior);
        setPreviewParam();
    }

    @Override
    protected void onError(@PiErrorCode int code, String message) {
        super.onError(code, message);
        Log.e(TAG, "ChangeResolution onError(" + code + ") ," + message);
    }

    public void changeSurfaceViewSize(Runnable work) {
        final CameraSurfaceView surfaceView;
        PilotSDK sdk = PilotSDK.mSingle;
        if (sdk != null) {
            surfaceView = sdk.mCameraSurfaceView;
        } else {
            surfaceView = null;
        }
        if (surfaceView == null || Objects.equals(surfaceView.getTag(), aspectRatio)) {
            Log.e(TAG, "changeSurfaceViewSize size not change !" + surfaceView);
            safeRun(work);
            return;
        }
        surfaceView.post(() -> {
            int[] ret = new int[2];
            if (isPanorama) {
                changeToPanorama(surfaceView);
            } else {
                changeToOtherRatio(surfaceView, aspectRatio, ret);
            }
            Log.d(TAG, "aspectRatio:" + aspectRatio + ",ret:" + Arrays.toString(ret));
            surfaceView.setTag(aspectRatio);
            if (work == null) {
                return;
            }
            if (isPanorama) {
                View parentView = (View) surfaceView.getParent();
                if (parentView == null || (parentView.getWidth() == surfaceView.getWidth() && parentView.getHeight() == surfaceView.getHeight())) {
                    work.run();
                    return;
                }
            } else if ((ret[0] == surfaceView.getWidth() && ret[1] == surfaceView.getHeight())) {
                work.run();
                return;
            }
            sExecutor.execute(() -> {
                while (true) {
                    View parentView = (View) surfaceView.getParent();
                    if (parentView == null) {
                        break;
                    }
                    if (isPanorama) {
                        if (parentView.getWidth() == surfaceView.getWidth() && parentView.getHeight() == surfaceView.getHeight()) {
                            break;
                        }
                    } else if ((ret[0] == surfaceView.getWidth() && ret[1] == surfaceView.getHeight())) {
                        break;
                    }
                    Log.w(TAG, "wait surface view size change finish...");
                    SystemClock.sleep(100);
                }
                surfaceView.post(work);
            });
        });
    }

    /**
     * 处理全景surface显示比例
     */
    protected boolean changeToPanorama(View surfaceView) {
        ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
        if (params.width == ViewGroup.LayoutParams.MATCH_PARENT
                && params.height == ViewGroup.LayoutParams.MATCH_PARENT) {
            return false;
        }
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        updateLayoutParams(surfaceView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        return true;
    }

    /**
     * 处理非全景surface比例
     */
    protected boolean changeToOtherRatio(View surfaceView, String aspectRatio, @NonNull int[] size) {
        View parentView = (View) surfaceView.getParent();
        SurfaceSizeUtils.calculateSurfaceSize(parentView.getWidth(), parentView.getHeight(), aspectRatio, size);
        return updateLayoutParams(surfaceView, size[0], size[1]);
    }

    protected boolean updateLayoutParams(View surfaceView, int width, int height) {
        Log.d(TAG, "updateLayoutParams,width:" + width + "," + height);
        ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) params).gravity = Gravity.CENTER;
        }
        boolean needChange = params.width != width && params.height != height;
        params.width = width;
        params.height = height;
        surfaceView.setLayoutParams(params);
        return needChange;
    }

    protected boolean isLockDefaultPreviewFps() {
        return true;
    }

    protected int[] getPreviewImageReaderFormat() {
        return null;
    }

    protected void initStabilizationTimeOffset() {
        PilotSDK.setStabilizationMediaHeightInfo(CameraPreview.CAMERA_PREVIEW_2900_2900_30[1]);
    }

    private void safeRun(Runnable run) {
        if (run != null) {
            run.run();
        }
    }
}
