package com.pi.pano;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.io.File;

public class ThumbMaker implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "ThumbMaker";

    private final long mNativeObj;
    private final Surface[] mSurfaces = new Surface[2];
    private final SurfaceTexture[] mSurfaceTextures = new SurfaceTexture[2];

    private ThumbMakerListener mThumbMakerListener;

    private int mNeedUpdateTextureCount;

    private boolean hasError;

    static {
        System.loadLibrary(Config.PIPANO_SO_NAME);
    }

    private static native long nativeCreateNativeObj();

    private static native void nativeRelease(long naviceObj);

    private static native int nativeGetFisheyeTextureId(long naviceObj, int index);

    private static native int nativeReadMediaFile(long naviceObj, String mediaFilename, int cameraCount, Surface[] surfaces);

    private static native int nativeMakeThumbnail
            (long naviceObj, String thumbFilename, int width, int height,
             float meshRotateX, float meshRotateY, float fov, float cameraDistance);

    ThumbMaker() {
        this(null);
    }

    ThumbMaker(Handler handler) {
        mNativeObj = nativeCreateNativeObj();
        for (int i = 0; i < mSurfaces.length; ++i) {
            mSurfaceTextures[i] = new SurfaceTexture(nativeGetFisheyeTextureId(mNativeObj, i));
            mSurfaces[i] = new Surface(mSurfaceTextures[i]);
            mSurfaceTextures[i].setOnFrameAvailableListener(this, handler);
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        try {
            surfaceTexture.updateTexImage();
        } catch (Exception e) {
            hasError = true;
            Log.e(TAG, "onFrameAvailable updateTexImage error :" + e.getMessage() + "," + getSrcName());
        }
        Log.d(TAG, "onFrameAvailable ==> " + getSrcName() + ",count:" + mNeedUpdateTextureCount);
        if (hasError) {
            if (mThumbMakerListener != null) {
                mThumbMakerListener.onMakeThumb(this, null);
            }
            mThumbMakerListener = null;
            return;
        }
        mNeedUpdateTextureCount--;
        if (mNeedUpdateTextureCount == 0) {
            if (mThumbMakerListener.mMediaFilename.endsWith(".jpg")) {
                nativeMakeThumbnail(mNativeObj, mThumbMakerListener.mThumbFilename,
                        mThumbMakerListener.mThumbWidth, mThumbMakerListener.mThumbHeight,
                        50.0f, 295f, 108f, 400.0f);
            } else {
                nativeMakeThumbnail(mNativeObj, mThumbMakerListener.mThumbFilename,
                        mThumbMakerListener.mThumbWidth, mThumbMakerListener.mThumbHeight,
                        180.0f, 90.0f, 130.0f, 400f);
            }
            mThumbMakerListener.onMakeThumb(this, mThumbMakerListener.mThumbFilename);
        }
    }

    public int makeThumbnail(ThumbMakerListener thumbMakerListener) {
        mThumbMakerListener = thumbMakerListener;
        hasError = false;
        File mediaFile = new File(thumbMakerListener.mMediaFilename);

        if (!mediaFile.exists()) {
            Log.e(TAG, "mediaFilename is not exits: " + thumbMakerListener.mMediaFilename);
            return 10;
        }

        mNeedUpdateTextureCount = Math.max(thumbMakerListener.mCameraCount, 1);
        if (mediaFile.isFile() && mediaFile.getName().toLowerCase().endsWith(".jpg")) {
            int ret = nativeReadMediaFile(mNativeObj, thumbMakerListener.mMediaFilename, thumbMakerListener.mCameraCount, null);
            if (ret != 0) {
                return ret;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(mediaFile.getAbsolutePath(), options);
            final int bitmapWidth = options.outWidth;

            options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(bitmapWidth / (bitmapWidth > 6000 ? 1080 : 2048), 1);
            Bitmap bitmap = BitmapFactory.decodeFile(mediaFile.getAbsolutePath(), options);

            mSurfaceTextures[0].setDefaultBufferSize(bitmap.getWidth(), bitmap.getHeight());
            Canvas canvas = mSurfaces[0].lockHardwareCanvas();
            canvas.drawBitmap(bitmap, 0, 0, null);
            mSurfaces[0].unlockCanvasAndPost(canvas);
            bitmap.recycle();
        } else if (mediaFile.isDirectory() || mediaFile.getName().toLowerCase().endsWith(".mp4")) {
            Surface[] surfaces = new Surface[mNeedUpdateTextureCount];
            System.arraycopy(mSurfaces, 0, surfaces, 0, surfaces.length);
            int ret = nativeReadMediaFile(mNativeObj, thumbMakerListener.mMediaFilename, thumbMakerListener.mCameraCount, surfaces);
            if (ret != 0) {
                return ret;
            }
        } else {
            Log.e(TAG, "mediaFilename is not jpg or dir");
            return 11;
        }
        return 0;
    }

    public void release() {
        nativeRelease(mNativeObj);
        for (SurfaceTexture s : mSurfaceTextures) {
            s.release();
        }
        for (Surface surface : mSurfaces) {
            surface.release();
        }
    }

    public static void test() {
        ThumbMaker thumbMaker = new ThumbMaker(null);
        ThumbMakerListener thumbMakerListener = new ThumbMakerListener() {
            @Override
            protected void onMakeThumb(ThumbMaker thumbMaker, String thumbFilename) {
                super.onMakeThumb(thumbMaker, thumbFilename);
            }
        };
        thumbMakerListener.mMediaFilename = "/sdcard/DCIM/Gallery/23-07-29-20-16-25_u";
        thumbMakerListener.mThumbFilename = thumbMakerListener.mMediaFilename + "_t.jpg";
        thumbMakerListener.mThumbWidth = 228;
        thumbMakerListener.mThumbHeight = 228;
        thumbMakerListener.mCameraCount = 2;
        int ret = thumbMaker.makeThumbnail(thumbMakerListener);
        if (ret != 0) {
            Log.e(TAG, "thumbMaker.makeThumbnail failed ret: " + ret);
        }
    }

    private String getSrcName() {
        return mThumbMakerListener == null ? null : mThumbMakerListener.mMediaFilename;
    }
}
