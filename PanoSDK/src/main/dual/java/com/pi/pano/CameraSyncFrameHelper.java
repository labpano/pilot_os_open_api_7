package com.pi.pano;

import android.util.Log;

import com.pi.pano.annotation.PiCameraBehavior;
import com.pi.pano.error.PiError;
import com.pi.pano.error.PiErrorCode;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class CameraSyncFrameHelper {

    private static final String TAG = CameraSyncFrameHelper.class.getSimpleName();

    public PiCallback createSyncFrameCallback(CameraToTexture[] mCameraToTexture,
                                              CallBackAdapter adapter) {
        return createSyncFrameCallback(mCameraToTexture, adapter, true);
    }

    public PiCallback createSyncFrameCallback(CameraToTexture[] mCameraToTexture,
                                              CallBackAdapter adapter, boolean needUpdatePreview) {
        int textureLength = mCameraToTexture.length;
        return new PiCallback() {
            final AtomicInteger result = new AtomicInteger(0);
            final AtomicReference<PiError> error = new AtomicReference<>(null);

            @Override
            public void onSuccess() {
                int value = result.incrementAndGet();
                Log.i(TAG, "createCallback onSuccess result : " + value + "," + textureLength);
                if (value == textureLength) {
                    PiError piError = error.get();
                    if (piError != null) {
                        CameraSyncFrameHelper.this.onError(adapter, piError);
                    } else {
                        if (needUpdatePreview) {
//                            new Thread(() -> updatePreview(mCameraToTexture, adapter)).start();
                            updatePreview(mCameraToTexture, adapter);
                        } else {
                            CameraSyncFrameHelper.this.onSuccess(adapter);
                        }
                    }
                }
            }

            @Override
            public void onError(PiError error) {
                int value = result.incrementAndGet();
                Log.e(TAG, "createCallback result: " + value + "," + textureLength + "," + error);
                this.error.set(error);
                if (value == textureLength) {
                    CameraSyncFrameHelper.this.onError(adapter, error);
                }
            }
        };
    }

    private void updatePreview(CameraToTexture[] mCameraToTexture, CallBackAdapter adapter) {
        Log.d(TAG, "updatePreview ===>" + Thread.currentThread().getName());
        int length = mCameraToTexture.length;
        final AtomicInteger result = new AtomicInteger(0);
        final AtomicReference<PiError> error = new AtomicReference<>(null);
        for (int i = length - 1; i >= 0; --i) {
            CameraToTexture c = mCameraToTexture[i];
            c.updatePreview(new PiCallback() {
                @Override
                public void onSuccess() {
                    synchronized (result) {
                        result.notifyAll();
                    }
                    int value = result.incrementAndGet();
                    Log.d(TAG, "updatePreview onSuccess result : " + value + "," + length);
                    if (value == length) {
                        PiError piError = error.get();
                        if (piError != null) {
                            CameraSyncFrameHelper.this.onError(adapter, piError);
                        } else {
                            CameraSyncFrameHelper.this.onSuccess(adapter);
                        }
                    }
                }

                @Override
                public void onError(PiError piError) {
                    synchronized (result) {
                        result.notifyAll();
                    }
                    int value = result.incrementAndGet();
                    Log.e(TAG, "updatePreview result: " + value + "," + length + ", " + piError);
                    error.set(piError);
                    if (value == length) {
                        CameraSyncFrameHelper.this.onError(adapter, piError);
                    }
                }
            });
            if (length >= 2 && i == 1) {
                synchronized (result) {
                    try {
                        result.wait();
                    } catch (InterruptedException e) {
                        onError(adapter, new PiError(PiErrorCode.CAMERA_SESSION_UPDATE_FAILED,
                                "updatePreview wait cameraId[1] failed :" + e.getMessage()));
                        return;
                    }
                }
            }
        }
    }

    private void onSuccess(CallBackAdapter adapter) {
        ChangeResolutionListener listener = adapter.listener();
        if (listener != null) {
            listener.onSuccess(PiCameraBehavior.OPEN);
            return;
        }
        PiCallback callback = adapter.callback();
        if (callback != null) {
            callback.onSuccess();
        }
    }

    private void onError(CallBackAdapter adapter, PiError error) {
        ChangeResolutionListener listener = adapter.listener();
        if (listener != null) {
            listener.onError(error.getCode(), error.getMessage());
            return;
        }
        PiCallback callback = adapter.callback();
        if (callback != null) {
            callback.onError(error);
        }
    }

    public interface CallBackAdapter {
        PiCallback callback();

        ChangeResolutionListener listener();
    }


    static class SimpleCallBackAdapter implements CallBackAdapter {
        private PiCallback mPiCallback;
        private ChangeResolutionListener mListener;

        public SimpleCallBackAdapter(PiCallback piCallback) {
            mPiCallback = piCallback;
        }

        public SimpleCallBackAdapter(ChangeResolutionListener listener) {
            mListener = listener;
        }

        @Override
        public PiCallback callback() {
            return mPiCallback;
        }

        @Override
        public ChangeResolutionListener listener() {
            return mListener;
        }
    }

}
