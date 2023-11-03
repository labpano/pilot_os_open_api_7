package com.pi.pano;

import android.media.Image;
import android.os.SystemClock;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * pano 工具方法。
 * TODO 整理中途临时存放。
 */
public final class PiPanoUtils {

    /**
     * 获取标定参数
     *
     * @return 标定参数
     */
    public static String getParam() {
        return PiPano.getParam();
    }

    /**
     * 是否是google街景视频,判断标准是是否包含camm track
     *
     * @param filename 视频文件名
     * @return 是否是google街景视频
     */
    public static boolean isGoogleStreetViewVideo(String filename) {
        return PiPano.isGoogleStreetViewVideo(filename);
    }

    public static void spatialMediaImpl(String filename, boolean isPano, String firmware, String artist) {
        PiPano.spatialMediaImpl(filename, isPano, false, firmware, artist, 1.0f);
    }

    public static void spatialMediaImpl(String filename, boolean isPano, boolean spatialPanoAudio, String firmware, String artist) {
        PiPano.spatialMediaImpl(filename, isPano, spatialPanoAudio, firmware, artist, 1.0f);
    }

    /**
     * 保存全景图片
     */
    public static int spatialJpeg(String filename, double heading, Image image, PiPano piPano) {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        return piPano.spatialJpeg(filename, buffer, width, height, heading,
                (System.currentTimeMillis() - SystemClock.elapsedRealtime()) * 1000 + image.getTimestamp() / 1000);
    }

    public static int injectThumbnail(File jpegFile, File thumbFile) {
        return injectThumbnail(jpegFile.getAbsolutePath(), thumbFile.getAbsolutePath());
    }

    /**
     * 添加缩略图到jpeg文件中,缩略图大小不能超过60000字节,如果jpeg文件原来就有缩略图,那么替换之
     *
     * @param jpegFilename  要添加缩略图的jpeg文件
     * @param thumbFilename 要添加或替换的缩略图文件,jpeg格式,不能超过60000字节
     * @return 返回0为正确
     */
    public static int injectThumbnail(String jpegFilename, String thumbFilename) {
        return PiPano.injectThumbnail(jpegFilename, thumbFilename);
    }

    public static final int DEVICE_MODEL_PILOT_ERA = 0;
    public static final int DEVICE_MODEL_PILOT_ONE = 1;
    // 广角设备
    public static final int DEVICE_MODEL_PILOT_WIDE = 2;

    /**
     * 设置固件版本号
     *
     * @param firmwareVersion 长度超过 9 byte会被截断
     */
    public static void setFirmware(String firmwareVersion) {
        PiPano.setFirmware(firmwareVersion);
    }

    /**
     * 获取当前SDK版本
     *
     * @return 当前SDK版本号
     */
    public static String getVersion() {
        return "1.2." + PiPano.getBuildNumber();
    }

    /**
     * 将多张鱼眼图像合并为一张鱼眼图像并展开拼接,用于去人影或分身拍照
     *
     * @param unstitchFilename 合并后的鱼眼图像文件名
     * @param fisheyeFilenames 用于合并的一组鱼眼图
     * @param mask             合并方式的掩码,如0x0112表示合并后的鱼眼图像的四个鱼眼,从左到右鱼眼分别来自fisheyeFilenames中的第0图,
     *                         第1图,第2图,第2图.
     * @return 错误码, 0为没错误
     */
    public static int makePanoWithMultiFisheye(@NonNull String unstitchFilename, String[] fisheyeFilenames, int mask) {
        return PiPano.makePanoWithMultiFisheye(unstitchFilename, fisheyeFilenames, mask, 2);
    }

    @IntDef({
            DEVICE_MODEL_PILOT_ERA,
            DEVICE_MODEL_PILOT_ONE,
            DEVICE_MODEL_PILOT_WIDE
    })
    public @interface PanoDeviceModel {
    }

    /**
     * 设置设备类型
     */
    public static void setDeviceModel(@PanoDeviceModel int model) {
        PiPano.setDeviceModel(model);
    }

    /**
     * 设置调试log文件夹目录
     */
    public static void setDev(String dirPath, boolean debug) {
        PiPano.sDebug = debug;
        // 文件名称固定为pilotSDK.log
        String logFilePath = new File(dirPath, "pilotSDK.log").getAbsolutePath();
        PiPano.setLogFilePath(logFilePath);
    }
}
