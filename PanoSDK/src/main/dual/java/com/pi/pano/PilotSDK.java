package com.pi.pano;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiAntiMode;
import com.pi.pano.annotation.PiCameraBehavior;
import com.pi.pano.annotation.PiCameraId;
import com.pi.pano.annotation.PiEncodeSurfaceType;
import com.pi.pano.annotation.PiFileStitchFlag;
import com.pi.pano.annotation.PiLensCorrectionMode;
import com.pi.pano.annotation.PiPreviewMode;
import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;
import com.pi.pano.wrap.ProParams;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 全景相关SDK,可用于全景展开,拼接,预览,拍照,设置录像直播接口,插入mp4全景信息
 */
class PilotSDK {
    private static final String TAG = "PilotSDK";

    /**
     * 全景相机的镜头数量
     */
    public static final int CAMERA_COUNT = 2;

    @SuppressLint("StaticFieldLeak")
    static PilotSDK mSingle;

    final CameraSurfaceView mCameraSurfaceView;

    private PanoSDKListener mPanoSDKListener;

    private final Context mContext;

    public PilotSDK(ViewGroup parentView, int openCameraCount, PanoSDKListener panoSDKListener) {
        this(parentView, openCameraCount, false, panoSDKListener);
    }

    /**
     * Activity onCreate的时候实例化PanoSDK
     *
     * @param parentView          PanoSDK包含一个预览View,这个是预览View的父View
     * @param openCameraCount     打开几个camera
     * @param lensProtectedEnable 是否带有保护镜
     * @param panoSDKListener     PanoSDK的回调接口
     */
    public PilotSDK(ViewGroup parentView, int openCameraCount, boolean lensProtectedEnable, PanoSDKListener panoSDKListener) {
        mContext = parentView.getContext();
        mPanoSDKListener = panoSDKListener;
        parentView.removeAllViews();// 移除已有的 cameraSurfaceView,
        mCameraSurfaceView = new CameraSurfaceView(mContext, openCameraCount);
        mCameraSurfaceView.initLensProtected(lensProtectedEnable);
        PanoSurfaceViewListener panoSurfaceViewListener = new PanoSurfaceViewListener() {
            @Override
            public void onPanoSurfaceViewCreate() {
                mSingle = PilotSDK.this;
                if (mPanoSDKListener != null) {
                    mPanoSDKListener.onPanoCreate();
                }
            }

            @Override
            public void onPanoSurfaceViewRelease() {
                if (mPanoSDKListener != null) {
                    mPanoSDKListener.onPanoRelease();
                }
                mSingle = null;
            }

            @Override
            public void onPanoModeChange(int mode) {
                if (mPanoSDKListener != null) {
                    mPanoSDKListener.onChangePreviewMode(mode);
                }
            }

            @Override
            public void onSingleTapConfirmed() {
                if (mPanoSDKListener != null) {
                    mPanoSDKListener.onSingleTap();
                }
            }

            @Override
            public void onEncodeFrame(int count) {
                if (mPanoSDKListener != null) {
                    mPanoSDKListener.onEncodeFrame(count);
                }
            }
        };
        mCameraSurfaceView.setOnPanoModeChangeListener(panoSurfaceViewListener);
        parentView.addView(mCameraSurfaceView);
    }

    private static boolean checkPanoSDKNotInit() {
        if (mSingle == null || mSingle.mCameraSurfaceView.mPiPano == null) {
            Log.e(TAG, "PilotSDK is not initialization, you have to call this function after onPanoSurfaceViewCreate");
            return true;
        }
        return false;
    }

    public static boolean isDestroy() {
        return mSingle == null || mSingle.mCameraSurfaceView.mPiPano == null || !mSingle.mCameraSurfaceView.mPiPano.mHasInit;
    }


    /**
     * 设置镜头畸变矫正模式
     * 一般设置为{@link PiLensCorrectionMode#PANO_MODE_2}
     *
     * @param mode 矫正模式：{@link PiLensCorrectionMode}
     */
    public static void setLensCorrectionMode(@PiLensCorrectionMode int mode) {
        if (mSingle != null) {
            Log.i(TAG, "setLensCorrectionMode mode: 0x" + Integer.toHexString(mode));
            mSingle.mCameraSurfaceView.setLensCorrectionMode(mode);
        }
    }

    /**
     * 重新加载水印
     *
     * @param show 是否显示
     */
    public static void reloadWatermark(boolean show) {
        if (mSingle != null) {
            mSingle.mCameraSurfaceView.mPiPano.reloadWatermark(show);
        }
    }

    public static ProParams getProParams() {
        if (checkPanoSDKNotInit()) {
            return null;
        }
        return mSingle.mCameraSurfaceView.getProParams();
    }

    /**
     * 设置拼接距离
     *
     * @param distance 0~100：0表示标定时候的距离,大概2m；100表示无穷远。
     *                 -1: 使用自动测拼接距离。
     */
    public static void setStitchingDistance(float distance, float max) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.d(TAG, "setStitchingDistance,distance:" + distance + ",max:" + max);
        if (distance < 0) {
            mSingle.mCameraSurfaceView.setStitchingDistance(distance / 100 * 5.0f);
        } else {
            mSingle.mCameraSurfaceView.setStitchingDistance(distance / 100 * max);
        }
    }

    /**
     * 设置预览模式
     *
     * @param mode           0-小行星 1-沉浸 2-鱼眼 3-平铺 5-平面模式,用于显示原始鱼眼,10-vlog模式
     * @param rotateDegree   0~360 初始旋转角度,平面模式下水平旋转,球形模式下绕Y轴旋转
     * @param playAnimation  切换显示模式的时候,是否播放切换动画
     * @param fov            0~180 初始纵向fov
     * @param cameraDistance 0~400 摄像机到球心的距离
     */
    public static void setPreviewMode(@PiPreviewMode int mode, float rotateDegree, boolean playAnimation, float fov, float cameraDistance) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "setPreviewMode,mode:" + mode + ",fov:" + fov + ",cameraDistance:" + cameraDistance);
        mSingle.mCameraSurfaceView.setPreviewMode(mode, rotateDegree, playAnimation, fov, cameraDistance);
    }

    /**
     * 是否开启/禁用触摸事件
     *
     * @param b 是否开启/禁用触摸事件
     */
    public static void setEnableTouchEvent(boolean b) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.setEnableTouchEvent(b);
    }

    /**
     * 拍照
     *
     * @param listener 拍照回调事件
     */
    public static void takePhoto(@NonNull TakePhotoListener listener) {
        if (checkPanoSDKNotInit()) {
            listener.dispatchTakePhotoComplete(PiErrorCode.NOT_INIT);
            return;
        }
        mSingle.mCameraSurfaceView.takePhoto(listener);
    }

    /**
     * 从屏幕获取画面拍照
     *
     * @return 0:成功
     */
    public static int pickPhoto(String filepath, int width, int height, TakePhotoListener3 listener3) {
        listener3.onTakePhotoStart();
        if (checkPanoSDKNotInit()) {
            return -1;
        }
        //
        @SuppressLint("WrongConstant")
        ImageReader imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireLatestImage();
                int width = image.getWidth();
                int height = image.getHeight();
                Image.Plane plane = image.getPlanes()[0];
                ByteBuffer buffer = plane.getBuffer();

                // copy pixel
                ByteBuffer dst = ByteBuffer.allocateDirect(width * height * 4);
                byte[] pixel = new byte[width * 4];
                for (int row = 0; row < height; row++) {
                    buffer.position(row * plane.getRowStride());
                    buffer.get(pixel);
                    dst.put(pixel);
                }
                dst.rewind();

                reader.close();
                setEncodeSurface(null, PiEncodeSurfaceType.SCREEN_PHOTO);

                if (saveFile(width, height, dst)) {
                    listener3.onTakePhotoComplete(filepath);
                } else {
                    listener3.onTakePhotoError(-1);
                }
            }

            private boolean saveFile(int width, int height, ByteBuffer buffer) {
                try (FileOutputStream stream = new FileOutputStream(filepath)) {
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return false;
            }
        }, null);
        setEncodeSurface(imageReader.getSurface(), PiEncodeSurfaceType.SCREEN_PHOTO);
        return 0;
    }

    public static void setPanoramaPreview(boolean reqPanoramaPreview) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        ((OpenGLThread) mSingle.mCameraSurfaceView.mPiPano).setPanoramaPreview(reqPanoramaPreview);
    }

    /**
     * 漫游中标记漫游点
     */
    public static void makeSlamPhotoPoint(String filename) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.mPiPano.makeSlamPhotoPoint(filename);
    }

    /**
     * 切换相机预览分辨率
     *
     * @param force 强制切换
     * @return 分辨率是否将改变
     */
    public static boolean changeCameraResolution(boolean force, ChangeResolutionListener listener) {
        if (checkPanoSDKNotInit()) {
            listener.onError(PiErrorCode.NOT_INIT, "");
            return false;
        }
        listener.forceChange = force;
        Log.i(TAG, "change camera resolution camera params: " + listener.toParamsString());
        if (!force) {
            if (listener.mWidth == mSingle.mCameraSurfaceView.getPreviewWidth()
                    && listener.mHeight == mSingle.mCameraSurfaceView.getPreviewHeight()
                    && listener.mFps == mSingle.mCameraSurfaceView.getPreviewFps()) {
                boolean noChanged;
                if ((PiCameraId.ASYNC_DOUBLE + "").equals(listener.mCameraId)) {
                    noChanged = getOpenCameraCount() == 2;
                } else {
                    noChanged = (Objects.equals(listener.mCameraId,
                            mSingle.mCameraSurfaceView.getCameraId()));
                }
                if (noChanged) {
                    Log.w(TAG, "change camera resolution not change,ignore!");
                    mSingle.mCameraSurfaceView.post(() ->
                            listener.onSuccess(PiCameraBehavior.UPDATE_PREVIEW)
                    );
                    return false;
                }
            }
        }
        mSingle.mCameraSurfaceView.mPiPano.changeCameraResolution(listener);
        return true;
    }

    public static int getOpenCameraCount() {
        if (checkPanoSDKNotInit()) {
            return -1;
        }
        return mSingle.mCameraSurfaceView.getCameraToTextureCount();
    }

    public static void setProParams(@NonNull ProParams params) {
        setProParams(params, null);
    }

    /**
     * pro参数设置
     *
     * @param params   pro
     * @param callback 回调
     */
    public static void setProParams(@NonNull ProParams params, @Nullable PiCallback callback) {
        if (checkPanoSDKNotInit()) {
            if (callback != null) {
                callback.onError(new PiError(PiErrorCode.NOT_INIT));
            }
            return;
        }
        Log.i(TAG, "setProParams:" + params);
        boolean notEmpty = params.exposeTime != null
                || params.exposureCompensation != null
                || params.iso != null
                || params.whiteBalance != null;
        if (notEmpty) {
            mSingle.mCameraSurfaceView.setProParams(params, callback);
            return;
        }
        if (callback != null) {
            callback.onSuccess();
        }
    }

    /**
     * 是否处理hdr拍照中。
     */
    public static void setInPhotoHdr(boolean hdrAble) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.setInPhotoHdr(hdrAble);
    }

    public static void setAntiMode(@PiAntiMode String value) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "setAntiMode:" + value);
        mSingle.mCameraSurfaceView.setAntiMode(value);
    }

    public static void setLockDefaultPreviewFps(boolean value) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "setLockDefaultPreviewFps:" + value);
        mSingle.mCameraSurfaceView.setLockDefaultPreviewFps(value);
    }

    /**
     * 预览时，需要准备的图像格式 ：支持 ImageFormat.JPEG , ImageFormat.RAW_SENSOR
     *
     * @param value {@link android.graphics.ImageFormat}
     */
    public static void setPreviewImageReaderFormat(int[] value) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.setPreviewImageReaderFormat(value);
    }

    private static VideoParams mVideoParams;

    private static MediaRecorderUtilBase[] mMediaRecorders;

    private static MediaRecorderUtil mMediaRecorderForThumb;

    /**
     * 获取当前镜头设置帧率
     */
    public static int getFps() {
        if (checkPanoSDKNotInit()) {
            return 0;
        }
        CameraEnvParams params = mSingle.mCameraSurfaceView.getCurCameraEnvParams();
        if (params != null) {
            if ((params.mWidth == 980 && params.mHeight == 980)
                    || (params.mWidth == 3868 && params.mHeight == 3868)) {
                // 8k 实际帧率仅5fps
                return 5;
            }
            return params.getFps();
        }
        return 0;
    }

    /**
     * 是否锁定默认预览帧率
     *
     * @return true ：锁定
     */
    public static boolean isLockDefaultPreviewFps() {
        if (checkPanoSDKNotInit()) {
            return false;
        }
        return mSingle.mCameraSurfaceView.isLockDefaultPreviewFps();
    }

    /**
     * 返回当前使用 camera的环境参数
     */
    public static CameraEnvParams getCurCameraEnvParams() {
        if (checkPanoSDKNotInit()) {
            return null;
        }
        return mSingle.mCameraSurfaceView.getCurCameraEnvParams();
    }

    /**
     * 开始录像,如果当前预览分辨率大于1000,那么会录制一个实时拼接的mp4,否则会录制4个原始鱼眼mp4
     *
     * @param params   录像参数
     * @param callback 回调
     *                 {@link PiErrorCode#NOT_INIT}, {@link PiErrorCode#CAMERA_NOT_OPENED} ,
     *                 {@link PiErrorCode#CAMERA_SESSION_NOT_CREATE}, {@link PiErrorCode#UN_KNOWN} ,
     *                 {@link PiErrorCode#CAMERA_SESSION_UPDATE_FAILED} ,
     *                 {@link PiErrorCode#CAMERA_SESSION_CONFIGURE_FAILED}
     */
    public static void startVideo(@NonNull VideoParams params, @NonNull PiCallback callback) {
        if (checkPanoSDKNotInit()) {
            callback.onError(new PiError(PiErrorCode.NOT_INIT, "NOT_INIT"));
            return;
        }
        if (null != mVideoParams) {
            stopRecord(false, false);
        }

        final CameraSurfaceView cameraSurfaceView = mSingle.mCameraSurfaceView;
        cameraSurfaceView.stopCaptureTag(false);

        int recorderCount = cameraSurfaceView.getCameraToTextureCount();
        if (recorderCount == 0) {
            callback.onError(new PiError(PiErrorCode.NOT_INIT, "CameraToTextureCount is 0"));
            return;
        }
        boolean isLargePreviewSize = cameraSurfaceView.isLargePreviewSize();
        boolean isStitched = (params.screenVideo) ||
                (isLargePreviewSize && (params.memomotionRatio == 0 || params.useForGoogleMap));
        if (isStitched) {
            File file = new File(params.getDirPath(), params.getBasicName() + PiFileStitchFlag.stitch + ".mp4");
            createDir(file.getParentFile());
            params.mCurrentVideoFilename = file.getAbsolutePath();
            recorderCount = 1;
        } else {
            File file = new File(params.getDirPath(), params.getBasicName() + PiFileStitchFlag.unstitch);
            createDir(file);
            params.mCurrentVideoFilename = file.getAbsolutePath();
        }
        mVideoParams = params;
        params.fillValue();
        int previewFps = cameraSurfaceView.getPreviewFps();
        if (previewFps == params.fps && params.cSize != null && params.cSize.length >= 3) {
            //fix 慢动作视频
            previewFps = params.cSize[2];
        }
        if (params.fps < 1) {
            params.fps = previewFps;
        }

        if (!isStitched) {
            cameraSurfaceView.mPiPano.setStabilizationFile(params.mCurrentVideoFilename + "/stabilization");
        }
        if (params.memomotionRatio >= 0) {
            PiPano.setMemomotionRatio(params.memomotionRatio);
        } else { // 慢动作，由编码器处理
            PiPano.setMemomotionRatio(0);
        }
        //非街景,Vlog录像时，关闭 间隔拼接
        boolean isVlog = !TextUtils.isEmpty(params.mCurrentVideoFilename) &&
                params.mCurrentVideoFilename.endsWith("_vlv_s.mp4");
        if (!params.useForGoogleMap && !isVlog) {
            cameraSurfaceView.mPiPano.setParamReCaliEnable(0, false);
        }
        mMediaRecorders = new MediaRecorderUtilBase[recorderCount];
        Surface[] surfaces = new Surface[recorderCount];
        for (int i = 0; i < recorderCount; ++i) {
            MediaRecorderUtilBase recorder = createMediaRecorderUtil(params.screenVideo ||
                    isLargePreviewSize || params.memomotionRatio != 0 || params.useForGoogleMap);
            recorder.setAudioEncoderExt(params.audioEncoderExtList);
            recorder.setAudioRecordExt(params.audioRecordExt);
            if (i == 0) {
                recorder.setCallback(callback);
            }
            String filename = isStitched ? params.mCurrentVideoFilename : params.mCurrentVideoFilename + "/" + i + ".mp4";
            Surface surface = recorder.startRecord(mSingle.mContext, filename,
                    EncodeConverter.convertVideoMime(params.encode),
                    params.videoWidth, params.videoHeight, params.fps, params.bitRate,
                    i == 0 ? params.channelCount : 0, params.useForGoogleMap, params.memomotionRatio, previewFps);
            if (surface == null) {
                abandonInStart(params, cameraSurfaceView, isStitched);
                callback.onError(new PiError(PiErrorCode.RECORD_ENCODE_CONFIGURE_FAILED, "MediaCodec video or audio init failed"));
                return;
            }
            mMediaRecorders[i] = recorder;
            surfaces[i] = surface;
        }
        params.saveConfig(cameraSurfaceView.mLensProtected, cameraSurfaceView.getFov());
        //录鱼眼时,保存preview.mp4
        Surface thumbSurface = null;
        boolean previewEnabled = previewFps < 100 && params.fps < 100; // 是否可开启预览
        if (!isStitched && previewEnabled) {
            mMediaRecorderForThumb = new MediaRecorderUtil();
            thumbSurface = mMediaRecorderForThumb.startRecord(mSingle.mContext,
                    params.mCurrentVideoFilename + "/preview.mp4", MediaFormat.MIMETYPE_VIDEO_AVC,
                    640, 320, params.fps, 640 * 320 * 2,
                    0, false, params.memomotionRatio, previewFps);
        }
        boolean success = false;
        if (isLargePreviewSize) {
            if (isStitched) {
                if (params.screenVideo) {
                    cameraSurfaceView.mPiPano.setEncodeSurface(surfaces[0], PiEncodeSurfaceType.SCREEN_CAPTURE);
                } else {
                    cameraSurfaceView.mPiPano.setEncodeSurface(surfaces[0], PiEncodeSurfaceType.RECORD);
                }
            } else {
                for (int i = 0; i < surfaces.length; ++i) {
                    cameraSurfaceView.mPiPano.setEncodeSurface(surfaces[i], PiEncodeSurfaceType.RECORD_TIME_LAPSE_0 + i);
                }
            }
            success = true;
        } else {
            setStabilizationMediaHeightInfo(params.videoHeight, false);
            if (!mSingle.mCameraSurfaceView.startCapture(surfaces, previewEnabled, callback, params.cSize)) {
                return;
            }
            boolean isPano = "2:1".equals(params.aspectRatio);
            if (isPano) {
                mSingle.mCameraSurfaceView.mPiPano.setParamReCaliEnable(getFps(), false);
            }
        }
        if (thumbSurface != null) {
            cameraSurfaceView.mPiPano.setEncodeSurface(thumbSurface, PiEncodeSurfaceType.RECORD_THUMB);
        }
        if (success) {
            callback.onSuccess();
        }
    }

    @NonNull
    private static MediaRecorderUtilBase createMediaRecorderUtil(boolean byPano) {
        if (byPano) {
            // 从 Pano 生成数据，使用 MediaCodec 编码，含：延时摄影、街景，及（镜头的）平面视频。
            return new MediaRecorderUtil();
        }
        int way = SystemPropertiesProxy.getInt(Config.PERSIST_DEV_PANO_MEDIACODE_WAY, -1);
        switch (way) {
            case 1:
                return new MediaRecorderUtil();
            default:
            case 2:
                return new MediaRecorderUtil2();
            case 3:
                return new MediaRecorderUtil3();
        }
    }

    /**
     * 录像中，重新打开camera
     */
    public static void reopenCameraInVideo(@NonNull PiCallback callback) {
        if (checkPanoSDKNotInit()) {
            callback.onError(new PiError(PiErrorCode.NOT_INIT));
            return;
        }

        final CameraSurfaceView cameraSurfaceView = mSingle.mCameraSurfaceView;
        final VideoParams params = mVideoParams;

        if (params != null && mMediaRecorders != null) {
            Log.d(TAG, "reopenCameraInCapture start ");
            Surface[] surfaces = new Surface[mMediaRecorders.length];
            for (int i = 0; i < mMediaRecorders.length; i++) {
                MediaRecorderUtilBase recorder = mMediaRecorders[i];
                Surface surface = recorder.getSurface();
                if (surface == null) {
                    callback.onError(new PiError(PiErrorCode.UN_KNOWN,
                            "surface is null : already stopVideo()"));
                    return;
                }
                surfaces[i] = surface;
            }
            int previewFps = cameraSurfaceView.getPreviewFps();
            if (previewFps == params.fps && params.cSize != null && params.cSize.length >= 3) {
                //fix 慢动作视频
                previewFps = params.cSize[2];
            }
            boolean previewEnabled = previewFps < 100 && params.fps < 100; // 是否可开启预览
            cameraSurfaceView.reopenCameraInCapture(surfaces, previewEnabled, params.cSize, callback);
        } else {
            callback.onError(new PiError(PiErrorCode.UN_KNOWN, "not need reopen by : already stopVideo()"));
        }
        Log.d(TAG, "reopenCameraInCapture end ");
    }

    private static void abandonInStart(@NonNull VideoParams params, @NonNull CameraSurfaceView cameraSurfaceView, boolean isStitched) {
        if (null != mMediaRecorders) {
            for (MediaRecorderUtilBase recorder : mMediaRecorders) {
                if (null != recorder) {
                    recorder.stopRecord(false, false, "", "", 0);
                }
            }
            mMediaRecorders = null;
        }
        if (isStitched) {
            cameraSurfaceView.mPiPano.setStabilizationFile(null);
        }
        deleteFile(new File(params.mCurrentVideoFilename));
        setParamReCaliEnable(getFps(), false);
        //
        mVideoParams = null;
    }

    private static void createDir(File file) {
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    private static void deleteFile(File file) {
        if (file.exists()) {
            if (file.isFile()) {
                file.delete();
            } else {
                File[] child = file.listFiles();
                if (child != null && child.length > 0) {
                    for (File c : child) {
                        deleteFile(c);
                    }
                }
                file.delete();
            }
        }
    }

    public static void stopRecord(boolean injectPanoMetadata, boolean continuous) {
        stopRecord(injectPanoMetadata, continuous, null, null);
    }

    /**
     * 停止录像
     *
     * @param continuous 处于连续录像中（分段录像），true:停止后将再开始录像
     */
    public static void stopRecord(boolean injectPanoMetadata, boolean continuous, PiCallback callback, Executor executor) {
        boolean stopCapture = false;
        CameraSurfaceView view = null;
        if (!checkPanoSDKNotInit()) {
            PiPano.setMemomotionRatio(0);
            view = mSingle.mCameraSurfaceView;
            if (view != null) {
                view.mPiPano.setEncodeSurface(null, PiEncodeSurfaceType.RECORD);
                view.mPiPano.setEncodeSurface(null, PiEncodeSurfaceType.RECORD_THUMB);
                view.mPiPano.setEncodeSurface(null, PiEncodeSurfaceType.RECORD_TIME_LAPSE_0);
                view.mPiPano.setEncodeSurface(null, PiEncodeSurfaceType.RECORD_TIME_LAPSE_1);
                view.mPiPano.setEncodeSurface(null, PiEncodeSurfaceType.SCREEN_CAPTURE);
                if (!view.isLargePreviewSize()) {
                    if (injectPanoMetadata) {
                        view.stopCaptureTag(true);
                        stopCapture = true;
                    }
                }
            }
        }
        final boolean finalStopCapture = stopCapture;
        final CameraSurfaceView finalView = view;
        if (executor != null) {
            executor.execute(() -> {
                try {
                    stopRecordInner(finalView, injectPanoMetadata, finalStopCapture, continuous);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (callback != null) {
                        callback.onError(new PiError(PiErrorCode.UN_KNOWN, "stopRecord error :" + e.getMessage()));
                    }
                }
            });
        } else {
            stopRecordInner(finalView, injectPanoMetadata, finalStopCapture, continuous);
            if (callback != null) {
                callback.onSuccess();
            }
        }
    }

    private static void stopRecordInner(CameraSurfaceView view, boolean injectPanoMetadata,
                                        boolean stopCapture, boolean continuousRecord) {
        if (stopCapture) {
            view.stopCaptureBySplit();
        }
        long timeout = SystemPropertiesProxy.getLong(Config.PERSIST_DEV_TIMEOUT_STOP_VIDEO,
                Config.MAX_STOP_VIDEO_TIMEOUT);
        if (null != mVideoParams) {
            Thread thread = new Thread(() -> {
                boolean isPano = "2:1".equals(mVideoParams.aspectRatio);
                String versionName = mVideoParams.mVersionName;
                String artist = mVideoParams.mArtist;
                String filename = null;
                for (int i = 0; i < mMediaRecorders.length; ++i) {
                    MediaRecorderUtilBase recorderUtil = mMediaRecorders[i];
                    if (recorderUtil != null) {
                        long firstRecordSensorTimestamp = 0;
                        if (mSingle != null && mSingle.mCameraSurfaceView != null) {
                            firstRecordSensorTimestamp = mSingle.mCameraSurfaceView.getFirstRecordSensorTimestamp(i);
                        }
                        String f = recorderUtil.stopRecord(injectPanoMetadata, isPano, versionName, artist, firstRecordSensorTimestamp);
                        if (null == filename) {
                            filename = f;
                        }
                        mMediaRecorders[i] = null;
                    }
                }
                if (mMediaRecorderForThumb != null) {
                    String thumbFilepath = mMediaRecorderForThumb.stopRecord(false, isPano, versionName, artist, 0);
                    PiPano.spatialMediaFromOldMp4(filename, thumbFilepath, isPano);
                    mMediaRecorderForThumb = null;
                }
                if (isPano && !checkPanoSDKNotInit()) {
                    mSingle.mCameraSurfaceView.mPiPano.setParamReCaliEnable(getFps() * 2, false);
                }
                mVideoParams = null;
            });
            thread.start();
            try {
                thread.join(timeout);
            } catch (Exception e) {
                Log.e(TAG, "stopRecord error :" + e.getMessage());
            }
        }
        Log.d(TAG, "stopRecord mp4 end ");
        if (!checkPanoSDKNotInit()) {
            mSingle.mCameraSurfaceView.mPiPano.setStabilizationFile(null);
            if (stopCapture && view != null) {
                if (!continuousRecord) {
                    CameraSurfaceView finalView = view;
                    final Object lock = new Object();
                    Thread thread = new Thread(() -> {
                        if (finalView != null) {
                            SystemClock.sleep(1000);
                            finalView.stopCaptureTag(false);
                            finalView.stopCaptureByVideo(new PiCallback() {
                                @Override
                                public void onSuccess() {
                                    Log.i(TAG, "stopCaptureByVideo onSuccess");
                                    PilotSDK.setPanoEnable(true);
                                    synchronized (lock) {
                                        lock.notifyAll();
                                    }
                                }

                                @Override
                                public void onError(PiError error) {
                                    Log.i(TAG, "stopCaptureByVideo onError :" + error);
                                    PilotSDK.setPanoEnable(true);
                                    synchronized (lock) {
                                        lock.notifyAll();
                                    }
                                }
                            });

                        }
                    });
                    thread.start();
                    try {
                        synchronized (lock) {
                            lock.wait(timeout);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    view.stopCaptureTag(false);
                }
            }
        }
        Log.d(TAG, "stopVideo end ");
    }

    /**
     * 是否在录像中
     */
    public static boolean isInRecord() {
        if (mVideoParams != null) {
            return true;
        }
        return mSingle.mCameraSurfaceView.isInCapture();
    }

    public static void startCapture(Surface surface) {
        //todo zhy 待处理
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.startCapture(new Surface[]{surface}, true);
    }

    public static void stopCapture() {
        stopCapture(true);
    }

    public static void stopCapture(boolean reStartPreview) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.stopCapture(true, reStartPreview);
    }

    /**
     * camera 当前是否在预览中
     */
    public static boolean isCameraPreviewing() {
        if (checkPanoSDKNotInit()) {
            return false;
        }
        return mSingle.mCameraSurfaceView.isCameraPreviewing();
    }

    /**
     * 是否使用陀螺仪,回放的时候打开陀螺仪,可以指哪看哪,录像的时候可以用于防抖
     *
     * @param open open 是否打开陀螺仪
     */
    public static void useGyroscope(boolean open) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        if (!SystemPropertiesProxy.getBoolean(Config.PERSIST_DEV_PANO_USE_GYROSCOPE_ENABLE, true)) {
            open = false;
            Log.w(TAG, "use gyroscope force forbidden.");
        }
        mSingle.mCameraSurfaceView.useGyroscope(open);
    }

    /**
     * 用于防抖效果的配置
     *
     * @param height camera分辨率高
     */
    public static void setStabilizationMediaHeightInfo(int height) {
        setStabilizationMediaHeightInfo(height, true);
    }

    public static void setStabilizationMediaHeightInfo(int height, boolean toCache) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.stabilizationMediaHeightInfo(height, toCache);
    }

    /**
     * 设置gps信息,gps信息将用于写入视频
     */
    public static void setLocationInfo(Location location) {
        MediaRecorderUtil.setLocationInfo(location);
    }

    /**
     * 设置用于编码surface ,SDK会渲染图形到这个surface上,并由MediaRecorder或MediaCodec编码
     *
     * @param surface 这个surface一般来自MediaRecorder.getSurface()或MediaCodec.createInputSurface()
     * @param type    类型：{@link PiEncodeSurfaceType}
     */
    public static void setEncodeSurface(Surface surface, @PiEncodeSurfaceType int type) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.mPiPano.setEncodeSurface(surface, type);
    }

    /**
     * 开启旋转锁定的时候,是否锁定轴
     *
     * @param yaw   是否锁定yaw
     * @param pitch 是否锁定pitch
     * @param roll  是否锁定roll
     * @param lerp  0~1当某个轴没有跟随的时候,这个值代表镜头跟随的松紧系数,值越大跟随越快
     */
    public static void stabSetLockAxis(boolean yaw, boolean pitch, boolean roll, float lerp) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "stabSetLockAxis:" + yaw + "," + pitch + "," + roll + "," + lerp);
        mSingle.mCameraSurfaceView.mPiPano.nativeStabSetLockAxis(yaw, pitch, roll, lerp);
    }

    /**
     * 设置倒立状态, 如果开启了防抖,那么倒立效果无效
     *
     * @param b 是否是倒立状态
     */
    public static void setUpsideDown(boolean b) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "setUpsideDown:" + b);
        mSingle.mCameraSurfaceView.mPiPano.setUpsideDown(b);
    }

    /**
     * 开启/关闭 slam
     *
     * @param enable                  是否开启/关闭 slam
     * @param lenForCalMeasuringScale 用于计算比例尺的距离
     * @param listener                监听器
     */
    public static void setSlamEnable(boolean enable, float lenForCalMeasuringScale, boolean useForImuToPanoRotation, SlamListener listener) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "setSlamEnable:" + enable);
        if (enable) {
            listener.lenForCalMeasuringScale = lenForCalMeasuringScale;
            listener.assetManager = mSingle.mContext.getAssets();
            listener.showPreview = useForImuToPanoRotation;
            listener.useForImuToPanoRotation = useForImuToPanoRotation;
            mSingle.mCameraSurfaceView.mPiPano.slamStart(listener);
        } else {
            mSingle.mCameraSurfaceView.mPiPano.slamStop();
        }
    }

    /**
     * slam暂停
     */
    public static void slamPause() {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "slamPause");
        mSingle.mCameraSurfaceView.mPiPano.slamPause();
    }

    /**
     * slam恢复暂停
     */
    public static void slamResume() {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "slamResume");
        mSingle.mCameraSurfaceView.mPiPano.slamResume();
    }

    public static void detectionStart(OnAIDetectionListener listener) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "detectionStart");
        mSingle.mCameraSurfaceView.mPiPano.setDetectionEnabled(true, listener);
    }

    public static void resetDetectionTarget() {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "detectionReset");
        mSingle.mCameraSurfaceView.mPiPano.resetDetectionTarget();
    }

    public static void detectionStop() {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "detectionStop");
        mSingle.mCameraSurfaceView.mPiPano.setDetectionEnabled(false, null);
    }

    /**
     * 是否使用镜头保护镜
     */
    public static void setLensProtected(boolean enabled) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        Log.i(TAG, "setLensProtected:" + enabled);
        mSingle.mCameraSurfaceView.setLensProtected(enabled);
    }

    /**
     * 开启或关闭重新计算参数功能
     *
     * @param executionInterval >0计算的拼接间隔 0关闭计算 <0计算一次
     * @param highQuality       true高质量模式,速度慢,用于后拼接 false低质量模式,速度快,用于屏幕预览
     */
    public static void setParamReCaliEnable(int executionInterval, boolean highQuality) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        if (!SystemPropertiesProxy.getBoolean(Config.PERSIST_DEV_PANO_RE_CALI_PARAM_ENABLE, true)) {
            executionInterval = 0;
            Log.w(TAG, "re cali param force forbidden.");
        }
        mSingle.mCameraSurfaceView.mPiPano.setParamReCaliEnable(executionInterval, highQuality);
    }

    public static void restorePreview() {
        restorePreview(null);
    }

    /**
     * 恢复 预览
     */
    public static void restorePreview(PiCallback callback) {
        if (checkPanoSDKNotInit()) {
            if (callback != null) {
                callback.onError(new PiError(PiErrorCode.NOT_INIT, "restorePreview error "));
            }
            return;
        }
        mSingle.mCameraSurfaceView.startPreview(callback);
    }


    /**
     * 拍照时，重新启动预览
     * 注：此方法需要在 拍照8K及以照片时调用，拍照结束后，需要调用 {@link #restorePreview()} ()}重新恢复预览
     */
    public static void startPreviewWithTakeLargePhoto(@NonNull PiCallback callback) {
        Log.d(TAG, "startPreviewWithTakeLargePhoto");
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.startPreviewWithTakeLargePhoto(callback, 0);
    }

    public static void startPreviewWithTakeLargePhoto(@NonNull PiCallback callback, int skipFrame) {
        Log.d(TAG, "startPreviewWithTakeLargePhoto");
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.startPreviewWithTakeLargePhoto(callback, skipFrame);
    }

    /**
     * pano预览是否可用
     *
     * @param enable false：预览不可用，将会绘制一帧高斯模糊
     */
    public static void setPanoEnable(boolean enable) {
        if (checkPanoSDKNotInit()) {
            return;
        }
        mSingle.mCameraSurfaceView.mPiPano.setPanoEnabled(enable);
    }

    /**
     * 主动释放pano
     * ，error见{@link PiErrorCode#NOT_INIT}{@link PiErrorCode#ALREADY_RELEASE}{@link PiErrorCode#FILTER_RELEASE_ING}
     *
     * @param callback 回调
     */
    public static void release(PiCallback callback) {
        if (checkPanoSDKNotInit()) {
            callback.onError(new PiError(PiErrorCode.NOT_INIT, "pano sdk not init !"));
            return;
        }
        mSingle.mCameraSurfaceView.setOnPanoModeChangeListener(null);
        mSingle.mPanoSDKListener = null;
        mSingle.mCameraSurfaceView.release(callback);
    }

    /**
     * camera 全局状态监听，临时添加，待优化(在 {@link PanoSDKListener#onPanoCreate()}之后设置)
     *
     * @param callback 回调 {@link PiErrorCode}
     */
    public static void setCommCameraCallback(PiCallback callback) {
        if (checkPanoSDKNotInit()) {
            callback.onError(new PiError(PiErrorCode.NOT_INIT, "pano sdk not init !"));
            return;
        }
        mSingle.mCameraSurfaceView.setCommCameraCallback(callback);
    }
}