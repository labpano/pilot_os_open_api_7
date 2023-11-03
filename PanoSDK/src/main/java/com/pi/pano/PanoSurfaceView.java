package com.pi.pano;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiEncodeSurfaceType;
import com.pi.pano.annotation.PiLensCorrectionMode;
import com.pi.pano.annotation.PiPreviewMode;
import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;
import com.pi.pano.wrap.life.LifecycleManager;
import com.pi.pano.wrap.life.PanoSurfaceStateManager;

/**
 * 全景view
 */
abstract class PanoSurfaceView extends SurfaceView implements PiPano.PiPanoListener {
    private static final String TAG = "PanoSurfaceView";
    private static final int MESSAGE_SURFACE_CREATE = 0x110;
    private static final int MESSAGE_SURFACE_CREATE_CHECK = 0x111;
    private static final int MESSAGE_SURFACE_DESTROY = 0x112;

    PiPano mPiPano;
    GestureDetector mGestureDetector;
    PanoSurfaceViewListener mPanoSurfaceViewListener;
    boolean mEnableTouchEvent = true;
    @PiLensCorrectionMode
    protected int mLensCorrectionMode;
    protected boolean mLensProtected = false;
    boolean mDelayOnCreateEvent;
    Context mContext;
    protected int mCameraCount;
    protected float mFov;
    /**
     * 缓存 防抖参数 height
     */
    protected int mCacheMediaHeight = 0;

    /**
     * 长按事件
     */
    private final Runnable mLongClickRunnable = () -> {
        try {
            Input.reset2();
        } catch (Exception e) {
            Log.e(TAG, "mLongClickRunnable reset error :" + e.getMessage());
        }
    };
    private boolean hasPostLongClick = false;
    protected final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            int what = msg.what;
            int state = mSurfaceStateManager.getState();
            Log.d(TAG, "handleMessage create ==> "
                    + (what == MESSAGE_SURFACE_CREATE) + ", curState:" + state);
            if (what == MESSAGE_SURFACE_CREATE) {
                doSurfaceCreate((SurfaceHolder) msg.obj, state);
                return;
            }
            if (what == MESSAGE_SURFACE_CREATE_CHECK) {
                checkStateInSurfaceCreate((SurfaceHolder) msg.obj);
                return;
            }
            if (what == MESSAGE_SURFACE_DESTROY) {
                doSurfaceDestroy(state);
            }
        }
    };

    protected LifecycleManager mLifecycleManager;
    protected final PanoSurfaceStateManager mSurfaceStateManager;

    public PanoSurfaceView(Context context) {
        this(context, null, 1);
    }

    public PanoSurfaceView(final Context context, AttributeSet attrs, int openCameraCount) {
        super(context, attrs);
        try {
            Input.reset();
        } catch (Throwable ignore) {
        }
        mContext = context;
        mCameraCount = openCameraCount;
        mSurfaceStateManager = new PanoSurfaceStateManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mLifecycleManager = new LifecycleManager((Activity) context);
        }
        boolean inEditMode = isInEditMode();
        Log.d(TAG, "init surfaceView edit mode :" + inEditMode);
        if (!inEditMode) {
            initGesture(context);
            getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    onSurfaceCreatedImpl(holder);
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    Log.d(TAG, "surfaceChanged  :" + format + ",," + width + "*" + height + "," + PanoSurfaceView.this);
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    onSurfaceDestroyedImpl();
                }
            });
        }
    }

    private void initGesture(Context context) {
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mPanoSurfaceViewListener != null) {
                    mPanoSurfaceViewListener.onSingleTapConfirmed();
                }
                return true;
            }
        });
        mGestureDetector.setIsLongpressEnabled(false);
    }

    protected void onSurfaceCreatedImpl(SurfaceHolder holder) {
        boolean filterSurfaceCreate = filterSurfaceCreateByInActive();
        removeSurfaceMsg();
        int state = mSurfaceStateManager.getState();
        Log.d(TAG, "surfaceCreated filter :" + filterSurfaceCreate + ",curState: " + state + "," + PanoSurfaceView.this);
        if (!filterSurfaceCreate) {
            if ((state == PanoSurfaceStateManager.State.DESTROY_START && mPiPano != null)
                    || state == PanoSurfaceStateManager.State.CREATE_FINISHED) {
                mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.CREATE_FINISHED);
                mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_SURFACE_CREATE, holder));
                return;
            }
            boolean init = state == PanoSurfaceStateManager.State.INITIALIZED;
            if (init || state == PanoSurfaceStateManager.State.DESTROY_ING
                    || state == PanoSurfaceStateManager.State.DESTROY_FINISHED
                    || state == PanoSurfaceStateManager.State.CREATE_START) {
                if (state == PanoSurfaceStateManager.State.CREATE_START) {
                    mHandler.removeMessages(MESSAGE_SURFACE_CREATE);
                }
                if (state == PanoSurfaceStateManager.State.DESTROY_FINISHED || init) {
                    mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.CREATE_START);
                }
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_SURFACE_CREATE, holder)
                        , init ? 0 : 200);
                return;
            }
            Log.w(TAG, "surfaceCreated filter by: " + state + "," + mPiPano);
        } else {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_SURFACE_CREATE_CHECK, holder),
                    400);
        }
    }

    private void removeSurfaceMsg() {
        mHandler.removeMessages(MESSAGE_SURFACE_DESTROY);
        mHandler.removeMessages(MESSAGE_SURFACE_CREATE);
        mHandler.removeMessages(MESSAGE_SURFACE_CREATE_CHECK);
    }

    protected void onSurfaceDestroyedImpl() {
        boolean filterSurfaceDestroy = filterSurfaceDestroyByActive();
        removeSurfaceMsg();
        int state = mSurfaceStateManager.getState();
        Log.d(TAG, "surfaceDestroyed  filter :" + filterSurfaceDestroy
                + ",curState: " + state + "," + mPiPano + "," + PanoSurfaceView.this);
        if (!filterSurfaceDestroy) {
            if (mPiPano != null) {
                if (mSurfaceStateManager.isDestroyScope()) {
                    Log.w(TAG, "surfaceDestroyed filter by isDestroyScope ");
                    return;
                }
                mHandler.sendEmptyMessageDelayed(MESSAGE_SURFACE_DESTROY, 200);
                return;
            }
            if (state == PanoSurfaceStateManager.State.CREATE_START) {
                mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.DESTROY_FINISHED);
            }
        }
    }

    private boolean filterSurfaceCreateByInActive() {
        if (mLifecycleManager != null) {
            return mLifecycleManager.isInActive();
        }
        return false;
    }

    private boolean filterSurfaceDestroyByActive() {
        if (mLifecycleManager != null) {
            return mLifecycleManager.isActive();
        }
        return false;
    }

    private void doSurfaceCreate(SurfaceHolder holder, int state) {
        Log.d(TAG, "doSurfaceCreate start ==> state :" + state);
        if (state == PanoSurfaceStateManager.State.DESTROY_FINISHED ||
                state == PanoSurfaceStateManager.State.CREATE_START) {
            mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.CREATE_ING);
            mPiPano = new OpenGLThread(PanoSurfaceView.this, mContext,
                    (PanoSurfaceView.this instanceof CameraSurfaceView), mCameraCount);
            mPiPano.setEncodeSurface(holder.getSurface(), PiEncodeSurfaceType.PREVIEW);
            return;
        }
        if (state == PanoSurfaceStateManager.State.CREATE_FINISHED) {
            mPiPano.setEncodeSurface(holder.getSurface(), PiEncodeSurfaceType.PREVIEW);
            return;
        }
        if (state == PanoSurfaceStateManager.State.DESTROY_ING) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_SURFACE_CREATE, holder), 100);
            return;
        }
        Log.w(TAG, "doSurfaceCreate filter by ==> " + state);
    }

    private void checkStateInSurfaceCreate(SurfaceHolder holder) {
        boolean filterSurfaceCreateByInActive = filterSurfaceCreateByInActive();
        LifecycleManager.State curState = mLifecycleManager.getCurrentState();
        int surfaceState = mSurfaceStateManager.getState();
        Log.d(TAG, "checkStateInSurfaceCreate :" + filterSurfaceCreateByInActive
                + ",lifeState: " + curState + ",surfaceState:" + surfaceState);
        if (mLifecycleManager.isActive(curState)) {
            if (filterSurfaceCreateByInActive) {
                //被过滤掉，但当前是activity是前台走create逻辑
                if (curState == LifecycleManager.State.RESUMED) {
                    doSurfaceCreate(holder, mSurfaceStateManager.getState());
                    return;
                }
                //还是start状态,继续延时
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_SURFACE_CREATE_CHECK, holder), 200);
                return;
            }
            if (surfaceState == PanoSurfaceStateManager.State.DESTROY_ING) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_SURFACE_CREATE_CHECK, holder), 200);
            } else if (surfaceState == PanoSurfaceStateManager.State.DESTROY_FINISHED ||
                    surfaceState == PanoSurfaceStateManager.State.CREATE_FINISHED) {
                doSurfaceCreate(holder, mSurfaceStateManager.getState());
            }
        }
    }

    private void doSurfaceDestroy(int state) {
        Log.d(TAG, "doSurfaceDestroy start, state :" + state + "," + mPiPano);
        if (state == PanoSurfaceStateManager.State.DESTROY_START ||
                state == PanoSurfaceStateManager.State.CREATE_FINISHED) {
            if (mPiPano != null) {
                LifecycleManager life = mLifecycleManager;
                if (state == PanoSurfaceStateManager.State.CREATE_FINISHED && life != null) {
                    LifecycleManager.State curState = life.getCurrentState();
                    if (life.isActive(curState)) {
                        Log.w(TAG, "doSurfaceDestroy filter by resume , start delay check ");
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_SURFACE_CREATE_CHECK, getHolder()), 200);
                        return;
                    }
                }
                mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.DESTROY_ING);
                mPiPano.release(null);
            } else {
                mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.DESTROY_FINISHED);
            }
            return;
        }
        Log.w(TAG, "doSurfaceDestroy filter by :" + state);
    }


    void initLensProtected(boolean enabled) {
        mLensProtected = enabled;
    }

    public void setLensProtected(boolean enabled) {
        if (mLensProtected != enabled) {
            mLensProtected = enabled;
            mPiPano.setLensProtected(mLensProtected);
        }
    }

    @Override
    public void onPiPanoInit(@NonNull PiPano pano) {
        Log.i(TAG, "onPiPanoInit surface created");
        pano.setLensProtected(mLensProtected);
        resetParamReCaliEnable();
        initPresentation();
        if (mPanoSurfaceViewListener != null) {
            mPanoSurfaceViewListener.onPanoSurfaceViewCreate();
        } else {
            mDelayOnCreateEvent = true;
        }
        mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.CREATE_FINISHED);
    }

    private void initPresentation() {
        DisplayManager displayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
        Display[] presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        if (presentationDisplays.length > 0) {
            Presentation presentation = new Presentation(mContext, presentationDisplays[0]) {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(new PresentationSurfaceView(mContext,
                            null, PanoSurfaceView.this));
                }
            };
            presentation.show();
        }
    }

    public void setOnPanoModeChangeListener(PanoSurfaceViewListener panoSurfaceViewListener) {
        mPanoSurfaceViewListener = panoSurfaceViewListener;
        if (mDelayOnCreateEvent) {
            mDelayOnCreateEvent = false;
            if (mPanoSurfaceViewListener != null) {
                mPanoSurfaceViewListener.onPanoSurfaceViewCreate();
            }
        }
    }

    public void setEnableTouchEvent(boolean b) {
        mEnableTouchEvent = b;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mEnableTouchEvent) {
            float[] pointPoses = new float[event.getPointerCount() * 2];
            for (int i = 0; i < event.getPointerCount(); ++i) {
                pointPoses[i * 2] = event.getX(i);
                pointPoses[i * 2 + 1] = event.getY(i);
            }
            Input.onTouchEvent2(event.getAction() & MotionEvent.ACTION_MASK, event.getPointerCount(), event.getEventTime() * 1000000, pointPoses);

            mGestureDetector.onTouchEvent(event);
            if (MotionEvent.ACTION_UP == event.getAction() || MotionEvent.ACTION_MOVE == event.getAction()) {
                handleLongClick(false);
            } else if (MotionEvent.ACTION_DOWN == event.getAction()) {
                handleLongClick(true);
            }
        }
        return true;
    }

    @Override
    public void onPiPanoDestroy(@NonNull PiPano pano) {
        Log.i(TAG, "onPiPanoDestroy start");
        mPiPano = null;
        onPanoReleaseCallback();
        mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.DESTROY_FINISHED);
        Log.i(TAG, "surface destroy end");
    }

    protected void onPanoReleaseCallback() {
        Log.d(TAG, "onPanoReleaseCallback :" + mPanoSurfaceViewListener);
        if (mPanoSurfaceViewListener != null) {
            mPanoSurfaceViewListener.onPanoSurfaceViewRelease();
        }
        Log.d(TAG, "onPanoReleaseCallback end ");
    }

    /**
     * 设置预览模式
     *
     * @param mode           {@link PiPreviewMode}
     * @param rotateDegree   0~360 初始旋转角度,平面模式下水平旋转,球形模式下绕Y轴旋转
     * @param playAnimation  切换显示模式的时候,是否播放切换动画
     * @param fov            0~180 初始纵向fov
     * @param cameraDistance 0~400 摄像机到球心的距离
     */
    public void setPreviewMode(@PiPreviewMode int mode, float rotateDegree, boolean playAnimation, float fov, float cameraDistance) {
        Input.setPreviewMode2(mode, rotateDegree, playAnimation, fov, cameraDistance);
        if (mPanoSurfaceViewListener != null) {
            mPanoSurfaceViewListener.onPanoModeChange(Input.getPreviewMode());
        }
        mFov = fov;
    }

    @Override
    public void onPiPanoEncodeFrame(int count) {
        if (mPanoSurfaceViewListener != null) {
            mPanoSurfaceViewListener.onEncodeFrame(count);
        }
    }

    /**
     * 设置拼接距离
     *
     * @param d -100~100 0表示标定时候的距离,大概2m,100表示无穷远,-100大概是0.5m
     */
    void setStitchingDistance(float d) {
        if (mPiPano == null) {
            Log.e(TAG, "setStitchingDistance mPiPano is null");
            return;
        }
        mPiPano.setStitchingDistance(d);
    }

    /**
     * 是否使用陀螺仪
     *
     * @param open 是否使用陀螺仪
     */
    public void useGyroscope(boolean open) {
        if (mPiPano == null) {
            Log.e(TAG, "useGyroscope mPiPano is null");
            return;
        }
        mPiPano.useGyroscope(open, null);
    }

    protected void resetParamReCaliEnable() {
        if (null != mPiPano) {
            // 默认的拼接距离测量为1次
            mPiPano.setParamReCaliEnable(-1, false);
        }
    }

    public boolean isLensProtected() {
        return mLensProtected;
    }

    public float getFov() {
        return mFov;
    }

    private void handleLongClick(boolean start) {
        if (start) {
            postDelayed(mLongClickRunnable, 2000);
            hasPostLongClick = true;
        } else if (hasPostLongClick) {
            removeCallbacks(mLongClickRunnable);
            hasPostLongClick = false;
        }
    }

    public void release(PiCallback callback) {
        int state = mSurfaceStateManager.getState();
        Log.d(TAG, "release() curState :" + state + "," + mPiPano);
        if (mLifecycleManager != null) {
            mLifecycleManager.release();
            mLifecycleManager = null;
        }
        if (state == PanoSurfaceStateManager.State.DESTROY_FINISHED || mPiPano == null) {
            callback.onError(new PiError(PiErrorCode.ALREADY_RELEASE, "pano is null"));
            return;
        }
        if (state == PanoSurfaceStateManager.State.DESTROY_ING) {
            callback.onError(new PiError(PiErrorCode.FILTER_RELEASE_ING, "pano release ing !"));
            return;
        }
        if (mPiPano != null) {
            mSurfaceStateManager.changeState(PanoSurfaceStateManager.State.DESTROY_ING);
            mPiPano.release(callback);
            return;
        }
        Log.w(TAG, "release() filter by: " + state);
    }

    public int getCameraCount() {
        return mCameraCount;
    }

    public void stabilizationMediaHeightInfo(int height, boolean toCache) {
        Log.d(TAG, "stabilizationMediaHeightInfo " + height + "," + toCache + "," + mPiPano);
        if (mPiPano != null) {
            mPiPano.nativeStabSetMediaHeightInfo(height);
        }
        if (toCache) {
            mCacheMediaHeight = height;
        }
    }

    public void restoreStabilizationMediaHeightInfo() {
        Log.d(TAG, "restoreStabilizationMediaHeightInfo " + mCacheMediaHeight + "," + mPiPano);
        if (mCacheMediaHeight != 0 && mPiPano != null) {
            mPiPano.nativeStabSetMediaHeightInfo(mCacheMediaHeight);
            mCacheMediaHeight = 0;
        }
    }

}
