package com.pi.pano.wrap;

import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiExposureCompensation;
import com.pi.pano.annotation.PiExposureTime;
import com.pi.pano.annotation.PiIso;
import com.pi.pano.annotation.PiWhiteBalance;

/**
 * pro参数配置
 */
public class ProParams {

    @PiExposureTime
    public Integer exposeTime;
    @PiExposureCompensation
    public Integer exposureCompensation;
    @PiIso
    public Integer iso;
    @PiWhiteBalance
    public String whiteBalance;

    /**
     * 3A模式
     */
    public int control_mode;
    /**
     * 场景模式
     */
    public int control_sceneMode;

    public ProParams() {
        resetSceneMode();
    }

    public ProParams(@PiExposureTime int exposeTime, @PiIso int iso) {
        this();
        this.exposeTime = exposeTime;
        this.iso = iso;
    }

    public ProParams(@PiExposureTime int exposeTime,
                     @PiExposureCompensation int exposureCompensation,
                     @PiIso int iso,
                     @PiWhiteBalance String wb) {
        this();
        this.exposeTime = exposeTime;
        this.exposureCompensation = exposureCompensation;
        this.iso = iso;
        whiteBalance = wb;
    }

    public void clear() {
        exposeTime = null;
        exposureCompensation = null;
        iso = null;
        whiteBalance = null;
    }

    public void resetSceneMode() {
        control_mode = CameraMetadata.CONTROL_MODE_AUTO;
        control_sceneMode = CaptureRequest.CONTROL_SCENE_MODE_DISABLED;
    }

    public void copyTo(ProParams to) {
        to.exposureCompensation = exposureCompensation;
        to.exposeTime = exposeTime;
        to.iso = iso;
        to.whiteBalance = whiteBalance;
        to.control_mode = control_mode;
        to.control_sceneMode = control_sceneMode;
    }

    /**
     * hdr模式配置
     */
    public void configHdrMode(){
        control_sceneMode = CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT;
        control_mode = CameraMetadata.CONTROL_MODE_USE_SCENE_MODE;
    }

    @NonNull
    @Override
    public String toString() {
        return "ProParams{" +
                "ExposeTime=" + exposeTime +
                ", Ev=" + exposureCompensation +
                ", Iso=" + iso +
                ", Wb='" + whiteBalance + '\'' +
                '}';
    }
}
