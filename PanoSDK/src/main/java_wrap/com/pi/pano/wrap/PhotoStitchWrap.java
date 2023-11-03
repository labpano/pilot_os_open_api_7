package com.pi.pano.wrap;

import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.pi.pano.ResolutionSize;
import com.pi.pano.StitchingOpticalFlow;
import com.pi.pano.SystemPropertiesProxy;
import com.pi.pano.ThumbnailGenerator;
import com.pi.pano.annotation.PiFileStitchFlag;
import com.pi.pano.annotation.PiResolution;
import com.pi.pilot.pano.sdk.BuildConfig;

import java.io.File;

/**
 * 照片拼接类型转换
 */
public class PhotoStitchWrap {

    private static final String TAG = PhotoStitchWrap.class.getSimpleName();

    /**
     * 图片拼接
     *
     * @param params 拼接参数
     * @return 拼接好的照片路径
     */
    public static String stitch(@NonNull PhotoStitchParams params) {
        if (TextUtils.isEmpty(params.targetFilePath)) {
            params.targetFilePath = generateStitchFilename(params.srcFilePath);
        }
        ResolutionSize size = null;
        try {
            if (!TextUtils.isEmpty(params.targetResolution)) {
                try {
                    size = ResolutionSize.parseSize(params.targetResolution);
                } catch (Exception e) {
                    Log.e(TAG, "stitch parseSize " + params.targetResolution + ",error :" + e.getMessage());
                }
            }
            if (size == null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(params.srcFilePath, options);
                Log.d(TAG, "stitch resolution start ===> " + options.outWidth + "," + options.outHeight);
                if (TextUtils.isEmpty(options.outMimeType)) {
                    return null;
                }
                String resolution = options.outWidth + "*" + options.outHeight;
                if (PiResolution._12K_c.equals(resolution)) {
                    size = ResolutionSize.parseSize(PiResolution._12K);
                } else if (PiResolution._5_7K_c.equals(resolution)) {
                    size = ResolutionSize.parseSize(PiResolution._5_7K);
                } else {
                    size = new ResolutionSize(options.outWidth, options.outHeight);
                }
            }
            Log.d(TAG, "stitch resolution result ===> " + size);
        } catch (Exception e) {
            Log.e(TAG, "stitch error :" + e.getMessage());
            return null;
        }
        StitchingOpticalFlow.stitchJpegFile(params.srcFilePath, params.targetFilePath,
                params.useOpticalFlow, params.lensProtectedEnable, params.quality, size.width, size.height);
        if (params.injectExifThumbnail) {
            ThumbnailGenerator.injectExifThumbnailForImage(new File(params.targetFilePath));
        }
        return params.targetFilePath;
    }


    /**
     * 由未拼接路径生成已拼接的路径（文件名）
     *
     * @param unstitchFilename 未拼接路径
     * @return 已拼接路径
     */
    public static String generateStitchFilename(String unstitchFilename) {
        int lastIndexOf;
        lastIndexOf = unstitchFilename.lastIndexOf(PiFileStitchFlag.unstitch + "_hdr.jpg");
        if (lastIndexOf > 0) {
            return unstitchFilename.substring(0, lastIndexOf) + PiFileStitchFlag.stitch + "_hdr.jpg";
        }
        lastIndexOf = unstitchFilename.lastIndexOf(PiFileStitchFlag.unstitch + ".jpg");
        if (lastIndexOf > 0) {
            return unstitchFilename.substring(0, lastIndexOf) + PiFileStitchFlag.stitch + ".jpg";
        }
        lastIndexOf = unstitchFilename.lastIndexOf(".jpg");
        if (lastIndexOf > 0) {
            return unstitchFilename.substring(0, lastIndexOf) + PiFileStitchFlag.stitch + ".jpg";
        }
        return unstitchFilename;
    }

    public static class PhotoStitchParams {
        /**
         * 未拼接源文件路径
         */
        public final String srcFilePath;
        /**
         * 拼接后文件路径
         */
        public String targetFilePath;
        /**
         * 拼接后照片分辨率, null对应源文件分辨率
         */
        public String targetResolution;
        /**
         * 是否使用光流拼接
         */
        public boolean useOpticalFlow = true;
        /**
         * 是否使用保护镜
         */
        public boolean lensProtectedEnable = false;
        /**
         * 是否给 输出 照片 注入缩略图
         */
        public boolean injectExifThumbnail = true;
        /**
         * 图片质量
         */
        public int quality = 99;

        private PhotoStitchParams(String srcFilePath) {
            this.srcFilePath = srcFilePath;
            if (srcFilePath.endsWith("_hdr.jpg")) {
                quality = 100;
            }
        }
    }

    public static class Factory {


        public static PhotoStitchParams create(String filePath, boolean lensProtectedEnable) {
            PhotoStitchParams params = new PhotoStitchParams(filePath);
            params.lensProtectedEnable = lensProtectedEnable;
            return params;
        }

        public static PhotoStitchParams create(String filePath, String targetFile, boolean lensProtectedEnable) {
            PhotoStitchParams params = new PhotoStitchParams(filePath);
            if (!TextUtils.isEmpty(targetFile)) {
                params.targetFilePath = targetFile;
            }
            params.lensProtectedEnable = lensProtectedEnable;
            boolean useOpticalFlow = true;
            if (BuildConfig.DEBUG) {
                if (SystemPropertiesProxy.getInt("persist.dev.photo.stitch.type", 0) == 1) {
                    useOpticalFlow = false;
                }
            }
            params.useOpticalFlow = useOpticalFlow;
            return params;
        }

        public static PhotoStitchParams createForShare(String filePath, String targetFile,
                                                       boolean lensProtectedEnable, int quality, String targetResolution) {
            PhotoStitchParams params = create(filePath, targetFile, lensProtectedEnable);
            params.quality = quality;
            params.targetResolution = targetResolution;
            return params;
        }
    }

}
