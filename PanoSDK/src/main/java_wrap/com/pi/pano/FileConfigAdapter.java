package com.pi.pano;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiFileStitchFlag;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * fileConfig 文件生成类
 */
class FileConfigAdapter implements IFileConfigAdapter {
    private static final String TAG = PiPanoConfig.TAG_PREFIX + "-ConfigSave";
    private static final ExecutorService sExecutor = Executors.newCachedThreadPool(
            r -> new Thread(r, TAG + "-" + PiPanoConfig.S_COUNT.incrementAndGet())
    );

    @Override
    public void saveConfig(@NonNull PhotoParams photoParams, boolean lensProtected) {
        if (!TextUtils.isEmpty(photoParams.unStitchDirPath)) {
            FileConfig config = new FileConfig();
            config.filePath = parsePhotoPath(photoParams);
            config.isPhoto = true;
            config.version = photoParams.software;
            config.resolution = photoParams.resolution;
            if (photoParams.isHdr()) {
                config.hdrCount = photoParams.hdrCount;
            }
            config.fittings = lensProtected ? 2 : 1;
            //
            sExecutor.execute(() -> {
                File configFile = generateConfigFile(config.filePath);
                if (configFile != null) {
                    JSONObject json = new JSONObject();
                    try {
                        json.put(Key.KEY_FITTINGS, config.getFittings());
                        json.put(Key.KEY_RESOLUTION, config.getResolution());
                        json.put(Key.KEY_VERSION, config.getVersion());
                        if (config.getHdrCount() > 0) {
                            json.put(Key.KEY_HDR_COUNT, config.getHdrCount());
                        }
                        modifyFileContent(configFile, json.toString(), false);
                    } catch (JSONException ignore) {
                    }
                }
            });
        }
    }

    @Override
    public void saveConfig(@NonNull VideoParams videoParams, boolean lensProtected, float fov) {
        if (!TextUtils.isEmpty(videoParams.getBasicName())
                && !TextUtils.isEmpty(videoParams.getDirPath())
                && "2:1".equals(videoParams.aspectRatio)
                && !TextUtils.isEmpty(videoParams.getCurrentVideoFilename())) {
            FileConfig config = new FileConfig();
            config.resolution = ResolutionSize.toResolutionSizeString(videoParams.videoWidth, videoParams.videoHeight);
            config.fps = videoParams.fps;
            config.bitrate = videoParams.bitRate;
            config.version = videoParams.mVersionName;
            config.timelapseRatio = videoParams.memomotionRatio;
            if (videoParams.audioEncoderExtList != null) {
                config.spatialAudio = videoParams.spatialAudio;
            }
            config.filePath = videoParams.getCurrentVideoFilename();
            config.fittings = lensProtected ? 2 : 1;
            config.fieldOfView = (int) fov;
            //
            sExecutor.execute(() -> {
                File configFile = generateConfigFile(config.filePath);
                if (configFile != null) {
                    JSONObject json = new JSONObject();
                    try {
                        json.put(Key.KEY_FITTINGS, config.getFittings());
                        json.put(Key.KEY_RESOLUTION, config.getResolution());
                        json.put(Key.KEY_VERSION, config.getVersion());
                        json.put(Key.KEY_FPS, config.getFps());
                        json.put(Key.KEY_BITRATE, config.getBitrate());
                        json.put(Key.KEY_SPATIAL_AUDIO, config.isSpatialAudio());
                        json.put(Key.KEY_FIELD_OF_VIEW, config.getFieldOfView());
                        json.put(Key.KEY_TIME_LAPSE_RATIO, config.getTimelapseRatio());
                        modifyFileContent(configFile, json.toString(), false);
                    } catch (JSONException ignore) {
                    }
                }
            });
        }
    }

    @NonNull
    private String parsePhotoPath(PhotoParams params) {
        String path;
        if (params.unStitchDirPath.endsWith("itp") ||
                params.unStitchDirPath.endsWith("btp") ||
                params.unStitchDirPath.contains("/DCIM/Tours/")) {
            //间隔拍照,连拍,漫游
            path = params.unStitchDirPath;
        } else {
            if (params.unStitchDirPath.endsWith("/.mf")) {
                //隐藏拍摄者
                path = params.unStitchDirPath;
                path = path.replace("/.mf", PiFileStitchFlag.unstitch);
            } else {
                //普通照片
                path = params.unStitchDirPath + File.separator
                        + params.obtainBasicName() + PiFileStitchFlag.unstitch;
            }
            if (params.isHdr()) {
                path += "_hdr";
            }
            path += ".jpg";
        }
        return path;
    }

    private static File generateConfigFile(String srcFilePath) {
        File rootFile = getConfigRootDirFile(srcFilePath);
        if (rootFile != null) {
            File file = new File(srcFilePath);
            return new File(rootFile, file.getName() + ".config");
        }
        return null;
    }

    @Nullable
    private static File getConfigRootDirFile(String srcFilePath) {
        final String tag = "/DCIM/";
        int index = srcFilePath.indexOf(tag);
        if (index > -1) {
            File rootFile = new File(srcFilePath.substring(0, index + tag.length()));
            rootFile = new File(rootFile, ".config");
            return rootFile;
        }
        return null;
    }

    private static void modifyFileContent(File file, String content, boolean append) {
        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, append))) {
            writer.append(content);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class FileConfig {
        /**
         * 配件类型 1：无配件 ，2 :保护镜
         */
        private int fittings = 1;
        private int fps;
        private long bitrate;
        /**
         * 分辨率 width*height
         */
        private String resolution;
        /**
         * 延时倍数
         */
        private int timelapseRatio;
        /**
         * 视场角度
         */
        private int fieldOfView = 90;
        private int hdrCount;
        /**
         * 全景音
         */
        private boolean spatialAudio;
        private String version;

        public String filePath;
        public boolean isPhoto;

        FileConfig() {
        }

        public int getFittings() {
            return fittings;
        }

        public int getFps() {
            return fps;
        }

        public long getBitrate() {
            return bitrate;
        }

        public String getResolution() {
            return resolution;
        }

        public int getTimelapseRatio() {
            return timelapseRatio;
        }

        public int getFieldOfView() {
            return fieldOfView;
        }

        public int getHdrCount() {
            return hdrCount;
        }

        public boolean isSpatialAudio() {
            return spatialAudio;
        }

        public String getVersion() {
            return version;
        }
    }
}
