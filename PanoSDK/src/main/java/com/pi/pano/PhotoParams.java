package com.pi.pano;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiPhotoFileFormat;
import com.pi.pano.annotation.PiPhotoResolution;
import com.pi.pilot.pano.sdk.BuildConfig;

/**
 * 拍照参数
 */
public final class PhotoParams {
    /**
     * 拍照后将生成缩略图（缩略图会注入到exif）
     */
    public boolean createThumb;
    /**
     * 是否保存.config文件
     */
    public boolean saveConfig = true;
    /**
     * (漫游拍照)是否记录定位点信息
     */
    public boolean makeSlamPhotoPoint = false;

    /**
     * hdr拍照采集的照片数量，即照片堆叠张数
     */
    public int hdrCount;

    public IFilenameProxy fileNameGenerator;

    /**
     * 基础的文件名
     */
    private String basicName;

    /**
     * 获取基础的文件名
     */
    public String obtainBasicName() {
        if (null == basicName) {
            basicName = fileNameGenerator.getBasicName();
        }
        return basicName;
    }

    /**
     * 拼接照片保存文件夹路径
     */
    public String stitchDirPath = "/sdcard/DCIM/Photos/Stitched/";
    /**
     * 未拼接照片保存的文件夹路径
     */
    public String unStitchDirPath = "/sdcard/DCIM/Photos/Unstitched/";
    /**
     * 分辨率
     */
    @PiPhotoResolution
    public String resolution;

    /**
     * 文件格式
     */
    @PiPhotoFileFormat
    public String fileFormat = PiPhotoFileFormat.jpg;
    /**
     * 作者
     */
    public String artist;
    /**
     * 版本
     */
    public String software;
    /**
     * 经度
     */
    public double longitude;
    /**
     * 维度
     */
    public double latitude;
    /**
     * 海拔
     */
    public int altitude;
    /**
     * 朝向
     */
    public double heading;
    /**
     * 是否保留hdr源文件
     */
    public boolean saveHdrSourceFile;

    private PhotoParams() {
    }

    /**
     * 设置位置信息
     *
     * @param longitude   经度
     * @param latitude    维度
     * @param altitude    海拔
     * @param orientation 朝向
     */
    public void setLocation(double longitude, double latitude, int altitude, float orientation) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
        this.heading = orientation;
    }

    /**
     * 是否是hdr拍照
     *
     * @return true:hdr拍照
     */
    public boolean isHdr() {
        return hdrCount > 0;
    }

    /**
     * hdr是否保存原文件（中间文件）
     */
    public boolean isSaveHdrSourceFile() {
        return hdrCount > 0 && saveHdrSourceFile;
    }

    @NonNull
    @Override
    public String toString() {
        return "{" +
                "resolution='" + resolution + '\'' +
                '}';
    }

    void saveConfig(boolean lensProtected) {
        if (saveConfig) {
            FileConfigAdapter adapter = new FileConfigAdapter();
            adapter.saveConfig(this, lensProtected);
        }
    }

    /**
     * 参数创建
     */
    public static final class Factory {

        @NonNull
        public static PhotoParams createParams(
                int hdrCount,
                String basicName,
                @PiPhotoResolution String resolution) {
            PhotoParams params = new PhotoParams();
            params.hdrCount = hdrCount;
            params.saveHdrSourceFile = hdrCount > 0 && requestSaveHdrSourceFile();
            params.basicName = basicName;
            params.resolution = resolution;
            return params;
        }

        @NonNull
        public static PhotoParams createParams(
                int hdrCount,
                IFilenameProxy fileNameGenerator, @Nullable String unStitchDirPath,
                @PiPhotoFileFormat String fileFormat,
                @PiPhotoResolution String resolution) {
            PhotoParams params = new PhotoParams();
            params.makeSlamPhotoPoint = false;
            params.hdrCount = hdrCount;
            params.saveHdrSourceFile = hdrCount > 0 && requestSaveHdrSourceFile();
            params.createThumb = true;
            //
            params.fileFormat = fileFormat;
            params.fileNameGenerator = fileNameGenerator;
            params.unStitchDirPath = unStitchDirPath;
            params.resolution = resolution;
            return params;
        }

        @NonNull
        public static PhotoParams createParams(
                boolean makeSlamPhotoPoint, int hdrCount, boolean takeThumb,
                IFilenameProxy fileNameGenerator, @Nullable String unStitchDirPath,
                @PiPhotoFileFormat String fileFormat,
                @PiPhotoResolution String resolution) {
            PhotoParams params = new PhotoParams();
            params.createThumb = takeThumb;
            params.makeSlamPhotoPoint = makeSlamPhotoPoint;
            params.hdrCount = hdrCount;
            params.saveHdrSourceFile = hdrCount > 0 && requestSaveHdrSourceFile();
            //
            params.fileNameGenerator = fileNameGenerator;
            params.unStitchDirPath = unStitchDirPath;
            params.fileFormat = fileFormat;
            params.resolution = resolution;
            return params;
        }
    }

    private static boolean requestSaveHdrSourceFile() {
        if (BuildConfig.DEBUG) {
            return SystemPropertiesProxy.getInt(
                    "persist.dev.photo.hdr.save.source", 1) == 1;
        }
        // 6.0 版本不保留中间文件
        return false;
    }
}

