package com.pi.pano;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.error.PiErrorCode;
import com.pi.pano.wrap.IMetadataCallback;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

public class PilotLib {
    private static final String TAG = "PilotLib";

    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    private static final AtomicBoolean sLoadPushLib = new AtomicBoolean(false);
    private IPreviewManager mPreview;
    private ICaptureManager mCaptureManager;
    private IPushManager mPushManager;

    private IPlayerManager mPlayerManager;
    private IStitchManager mStitchManager;
    private IMetadataCallback mMetadataCallback;
    private Context mContext;

    //region public

    public static void init(Context context) {
        if (INIT.get()) {
            return;
        }
        INIT.set(true);
        PilotLib self = self();
        self.mContext = context.getApplicationContext();
        self.mPreview = new PreviewManagerImpl();
        self.mCaptureManager = new CaptureManagerImpl();
        self.mPlayerManager = new PlayerManagerImpl();
        self.mStitchManager = new StitchManagerImpl();
    }

    /**
     * 预览接口
     */
    public static IPreviewManager preview() {
        return self().mPreview;
    }

    /**
     * 拍照录像接口
     */
    public static ICaptureManager capture() {
        return self().mCaptureManager;
    }

    /**
     * 推流管理接口
     */
    @Nullable
    public static IPushManager livePush() throws IllegalStateException {
        if (!sLoadPushLib.get()) {
            sLoadPushLib.set(true);
            Thread thread = Thread.currentThread();
            thread.setContextClassLoader(self().getClass().getClassLoader());
            Iterator<IPushManager> iterator = ServiceLoader.load(IPushManager.class).iterator();
            while (iterator.hasNext()) {
                IPushManager next = iterator.next();
                Log.d(TAG, "livePush next====>" + next);
                if (next != null) {
                    self().mPushManager = next;
                    break;
                }
            }
        }
        if (self().mPushManager == null) {
            throw new IllegalStateException("no found pushSDK,please implements com.pi.pano.IPushManager");
        }
        return self().mPushManager;
    }

    /**
     * 照片,视频播放 接口
     */
    public static IPlayerManager player() {
        return self().mPlayerManager;
    }

    /**
     * 拼接管理接口
     */
    public static IStitchManager stitch() {
        return self().mStitchManager;
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * camera 全局状态监听，(在 {@link PanoSDKListener#onPanoCreate()}之后设置)
     *
     * @param callback 回调 {@link PiErrorCode}
     */
    public void setCommCameraCallback(@NonNull PiCallback callback) {
        PilotSDK.setCommCameraCallback(callback);
    }

    public IMetadataCallback getMetadataCallback() {
        return mMetadataCallback;
    }

    /**
     * 设置 Metadata数据接口，sdk内会在拍照录像，拼接时，获取该接口返回数据 写入文件信息中
     *
     * @param metadataCallback 回调
     */
    public void setMetadataCallback(IMetadataCallback metadataCallback) {
        mMetadataCallback = metadataCallback;
    }

    /**
     * 主动释放pano
     *
     * @param callback 回调: error见{@link PiErrorCode#NOT_INIT}
     *                 <p> {@link PiErrorCode#ALREADY_RELEASE}
     *                 <p>{@link PiErrorCode#FILTER_RELEASE_ING}
     */
    public void release(PiCallback callback) {
        PilotSDK.release(callback);
    }

    /**
     * 检测pano是否销毁
     * 见 ： {@link PanoSDKListener#onPanoCreate()} ，{@link PanoSDKListener#onPanoRelease()}
     *
     * @return true: 已经释放 or未初始化完毕
     */
    public boolean isDestroy() {
        return PilotSDK.isDestroy();
    }

    //endregion

    //region instance

    public static PilotLib self() {
        return Holder.INSTANCE;
    }

    private PilotLib() {
    }

    private static final class Holder {
        private static final PilotLib INSTANCE = new PilotLib();
    }

    //endregion

}
