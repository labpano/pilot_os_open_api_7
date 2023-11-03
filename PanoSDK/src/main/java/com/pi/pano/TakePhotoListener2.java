package com.pi.pano;

import com.pi.pano.error.PiErrorCode;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 拍照回调。
 * 一次拍照可能拍多张照片，完成回调时将根据计数仅回调一次完成
 */
public abstract class TakePhotoListener2 extends TakePhotoListener {

    private int totalTakeCount = 1;
    private final AtomicInteger takeCount = new AtomicInteger(0);

    public void setTotalTakeCount(int count) {
        totalTakeCount = count;
    }

    void dispatchTakePhotoComplete(int errorCode) {
        if (PiErrorCode.NOT_INIT == errorCode ||
                PiErrorCode.CAMERA_NOT_OPENED == errorCode ||
                PiErrorCode.CAMERA_SESSION_NOT_CREATE == errorCode) {
            onTakePhotoComplete(errorCode);
            return;
        }
        if (takeCount.incrementAndGet() >= totalTakeCount) {
            onTakePhotoComplete(errorCode);
        }
    }
}
