package com.pi.pano;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiResolution;
import com.pi.pano.annotation.PiVideoEncode;
import com.pi.pano.annotation.PiVideoResolution;
import com.pi.pano.ext.IAudioEncoderExt;
import com.pi.pano.ext.IAudioRecordExt;
import com.pi.pano.wrap.annotation.PiFps;
import com.pi.pano.wrap.annotation.PiStreetVideoFps;
import com.pi.pano.wrap.annotation.PiTimeLapseTimes;

import java.util.List;

/**
 * 录像参数
 */
public final class VideoParams {
    /**
     * 音频比特率，固定值
     */
    public static final int AUDIO_BITRATE = MediaRecorderUtil.AUDIO_BITRATE;

    String mCurrentVideoFilename = "";

    /**
     * 文件名称
     */
    @NonNull
    final IRecordFilenameProxy filenameProxy;

    public final int videoWidth;
    public final int videoHeight;
    public final int channelCount;
    public int fps;
    public int bitRate;

    @PiVideoEncode
    public String encode = PiVideoEncode.h_264;
    /**
     * 画面比例
     */
    public String aspectRatio = "2:1";
    /**
     * 版本号（用于写入视频信息中）
     */
    public String mVersionName;
    /**
     * 作者 (用于写入视频信息中）
     */
    public String mArtist;

    /**
     * 延时摄影倍率,如果当前是7fps,如果ratio为70,那么会降低帧为7/70=0.1fps,
     * 也就是10s录一帧,只对实时拼接录像有效
     */
    public int memomotionRatio = 0;
    /**
     * 是否是用于google map
     */
    public boolean useForGoogleMap = false;
    public float streetBitrateMultiple = 1;
    public boolean spatialAudio;
    /**
     * 音频编码扩展处理器
     */
    @Nullable
    public List<IAudioEncoderExt> audioEncoderExtList;
    /**
     * 录音接口
     */
    @Nullable
    public IAudioRecordExt audioRecordExt;
    /**
     * 是否保存.config文件
     */
    public boolean saveConfig = true;
    /**
     * 录制的视频文件存放目录
     */
    @Nullable
    private String dirPathInner;
    /**
     * 录制的视频文件名称
     */
    private String basicNameInner;
    /**
     * 屏幕视频(双码流镜头--录制当前屏幕)
     */
    public boolean screenVideo;

    /**
     * 录像中camera出图分辨率、帧率
     */
    @Nullable
    int[] cSize = null;

    public VideoParams(@NonNull IRecordFilenameProxy filenameProxy, int videoWidth, int videoHeight,
                       int fps, int bitRate, int channelCount) {
        this.filenameProxy = filenameProxy;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.fps = fps;
        this.bitRate = bitRate;
        this.channelCount = channelCount;
    }

    /**
     * 获取当前正在录像的文件路径
     */
    public String getCurrentVideoFilename() {
        return mCurrentVideoFilename;
    }

    void saveConfig(boolean lensProtected, float fov) {
        if (saveConfig) {
            FileConfigAdapter adapter = new FileConfigAdapter();
            adapter.saveConfig(this, lensProtected, fov);
        }
    }

    /**
     * 使用前检查并补全值
     */
    void fillValue() {
        if (bitRate <= 0) {
            bitRate = videoWidth * videoHeight * 3 * 2;
        }
    }

    @NonNull
    public String getDirPath() {
        if (null == dirPathInner) {
            dirPathInner = filenameProxy.getParentPath().getAbsolutePath();
        }
        return dirPathInner;
    }

    @NonNull
    public String getBasicName() {
        if (null == basicNameInner) {
            basicNameInner = filenameProxy.getBasicName();
        }
        return basicNameInner;
    }

    /**
     * 重用本参数
     */
    public void reuse() {
        basicNameInner = null;
    }

    @NonNull
    @Override
    public String toString() {
        return "RecordParams{" +
                "filenameProxy=" + filenameProxy +
                ", videoWidth=" + videoWidth +
                ", videoHeight=" + videoHeight +
                ", channelCount=" + channelCount +
                ", fps=" + fps +
                ", bitRate=" + bitRate +
                ", encode='" + encode + '\'' +
                ", aspectRatio='" + aspectRatio + '\'' +
                ", mVersionName='" + mVersionName + '\'' +
                ", mArtist='" + mArtist + '\'' +
                ", forceRecordStitch=" + false +
                ", memomotionRatio=" + memomotionRatio +
                ", useForGoogleMap=" + useForGoogleMap +
                ", streetBitrateMultiple=" + streetBitrateMultiple +
                ", spatialAudio=" + spatialAudio +
                ", audioEncoderExtList=" + audioEncoderExtList +
                ", audioRecordExt=" + audioRecordExt +
                ", saveConfig=" + saveConfig +
                ", dirPathInner='" + dirPathInner + '\'' +
                ", basicNameInner='" + basicNameInner + '\'' +
                '}';
    }

    /**
     * 参数创建
     */
    public static final class Factory {
        /**
         * 鱼眼、实时
         */
        public static VideoParams create(
                IRecordFilenameProxy filenameProxy,
                @PiVideoResolution String resolution, int fps, @PiVideoEncode String encode,
                int channelCount
        ) {
            // 双mp4
            ResolutionSize size;
            int[] cSize;
            switch (resolution) {
                case PiResolution._8K:
                    size = new ResolutionSize(3868, 3868);
                    fps = 10;
                    cSize = CameraPreview.CAMERA_PREVIEW_980_980_10_v;
                    break;
                case PiResolution._5_7K:
                    size = new ResolutionSize(2900, 2900);
                    fps = 30;
                    cSize = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    break;
                case PiResolution._4K:
                    size = new ResolutionSize(1932, 1932);
                    fps = 60;
                    cSize = CameraPreview.CAMERA_PREVIEW_940_940_60_v;
                    break;
                case PiResolution._2_5K:
                    size = new ResolutionSize(1220, 1220);
                    fps = 120;
                    cSize = CameraPreview.CAMERA_PREVIEW_1220_1220_120_v;
                    break;
                default:
                    throw new RuntimeException("Record don't support resolution:" + resolution + ",fps:" + fps);
            }
            VideoParams param = new VideoParams(filenameProxy,
                    size.width, size.height, fps,
                    getBitrate(resolution, String.valueOf(fps))
                    , channelCount);
            param.encode = encode;
            param.cSize = cSize;
            return param;
        }

        /**
         * 平面视频
         */
        public static VideoParams createForPlaneVideo(
                IRecordFilenameProxy filenameProxy,
                @PiVideoResolution String resolution, String aspectRatio, int fps,
                @PiVideoEncode String encode, int channelCount
        ) {
            // 单mp4
            // 计算比例
            String[] aa = resolution.split("\\*");
            if (aa.length != 2) {
                throw new RuntimeException("比例错误");
            }
            if (fps <= 0) {
                fps = PilotSDK.getFps();
            }
            int videoWidth = Integer.parseInt(aa[0]);
            int videoHeight = Integer.parseInt(aa[1]);

            VideoParams param = new VideoParams(filenameProxy,
                    videoWidth, videoHeight, fps,
                    getBitRateForPlaneVideo(videoWidth, videoHeight, aspectRatio)
                    , channelCount);
            param.aspectRatio = aspectRatio;
            param.encode = encode;
            return param;
        }

        /**
         * 慢动作
         */
        public static VideoParams createForSlowMotionVideo(
                IRecordFilenameProxy filenameProxy, @PiVideoEncode String encode,
                int channelCount, int times
        ) {
            // 双mp4
            VideoParams param = new VideoParams(filenameProxy,
                    1220, 1220, 30,
                    getBitRateForSlowMotionVideo(), channelCount);
            param.encode = encode;
            param.memomotionRatio = times;
            param.cSize = CameraPreview.CAMERA_PREVIEW_920_920_120_v;
            return param;
        }

        /**
         * 延时摄影
         */
        public static VideoParams createForTimeLapseVideo(
                IRecordFilenameProxy filenameProxy,
                @PiVideoResolution String resolution, @PiVideoEncode String encode,
                int channelCount, @PiTimeLapseTimes String times
        ) {
            // 双mp4
            ResolutionSize size;
            switch (resolution) {
                case PiResolution._8K:
                    size = new ResolutionSize(3868, 3868);
                    break;
                case PiResolution._5_7K:
                    size = new ResolutionSize(2900, 2900);
                    break;
                default:
                    throw new RuntimeException("TimeLapseVideo don't support resolution:" + resolution);
            }
            VideoParams param = new VideoParams(filenameProxy,
                    size.width, size.height, 30,
                    getBitRateForTimeLapseVideo(), channelCount);
            param.memomotionRatio = getTimeLapseRatio(times);
            param.encode = encode;
            return param;
        }

        /**
         * 街景视频
         */
        public static VideoParams createForStreetViewVideo(
                IRecordFilenameProxy filenameProxy,
                @PiVideoResolution String resolution, int fps, String encode,
                int channelCount,
                float streetBitrateMultiple
        ) {
            // 单mp4
            ResolutionSize size;
            int[] cSize;
            switch (resolution) {
                case PiResolution._8K:
                    size = new ResolutionSize(7680, 3840);
                    cSize = CameraPreview.CAMERA_PREVIEW_3868_3868_10;
                    break;
                case PiResolution._5_7K:
                    size = new ResolutionSize(5760, 2880);
                    String _fps = String.valueOf(fps);
                    if (PiFps._7.equals(_fps) || PiFps._2.equals(_fps) || PiFps._1.equals(_fps)) {
                        cSize = CameraPreview.CAMERA_PREVIEW_2900_2900_14;
                    } else if (PiFps._4.equals(_fps)) {
                        cSize = CameraPreview.CAMERA_PREVIEW_2900_2900_16;
                    } else if (PiFps._3.equals(_fps)) {
                        cSize = CameraPreview.CAMERA_PREVIEW_2900_2900_15;
                    } else {
                        throw new RuntimeException("StreetViewVideo don't support:" + resolution + ",fps:" + fps);
                    }
                    break;
                default:
                    throw new RuntimeException("StreetViewVideo don't support resolution:" + resolution);
            }
            VideoParams param = new VideoParams(filenameProxy,
                    size.width, size.height, fps,
                    getBitrateForStreetView(streetBitrateMultiple),
                    channelCount);
            param.useForGoogleMap = true;
            param.streetBitrateMultiple = streetBitrateMultiple;
            param.memomotionRatio =
                    getFpsRatioForStreetVideo(PiResolution._8K.equals(resolution) ?
                            10 : PilotSDK.getFps(), String.valueOf(fps));
            if (PiResolution._8K.equals(resolution)) {
                param.fps = 10;
            }
            param.encode = encode;
//            param.cSize = cSize;
            return param;
        }

        /**
         * 全景Vlog视频
         */
        public static VideoParams createForVlogVideo(
                IRecordFilenameProxy filenameProxy, int fps, String encode,
                int channelCount
        ) {
            // 单mp4
            ResolutionSize size;
            size = new ResolutionSize(3840, 1920);
            VideoParams param = new VideoParams(filenameProxy,
                    size.width, size.height, fps,
                    getBitrateForVlog(), channelCount);
            param.encode = encode;
            return param;
        }

        /**
         * 智能跟拍视频
         */
        public static VideoParams createForScreenVideo(
                IRecordFilenameProxy filenameProxy,  int fps, String encode,
                int channelCount, String aspectRatio
        ) {
            int width = 1920;
            int height = 1080;
            try {
                String[] split = aspectRatio.split(":");
                int w = Integer.parseInt(split[0]);
                int h = Integer.parseInt(split[1]);
                width = w > h ? 1920 : 1080;
                height = w > h ? 1080 : 1920;
            } catch (Exception e) {
                e.printStackTrace();
            }
            VideoParams param = new VideoParams(filenameProxy,
                    width, height, fps,
                    getBitRateForPlaneVideo(width, height, aspectRatio), channelCount);
            param.encode = encode;
            return param;
        }

        /**
         * 单mp4文件码率
         */
        public static int getBitrate(String resolution, String fps) {
            switch (resolution) {
                case PiResolution._5_7K:
                    return 120 * 1024 * 1024 / 2;
                case PiResolution._4K:
                    if (PiFps._60.equals(fps)) {
                        return 120 * 1024 * 1024 / 2;
                    }
                    return 60 * 1024 * 1024 / 2;
                case PiResolution._2_5K:
                    if (PiFps._120.equals(fps)) {
                        return 95 * 1024 * 1024 / 2;
                    }
                    return 80 * 1024 * 1024 / 2;
                default:
                    return 60 * 1024 * 1024 / 2;
            }
        }

        /**
         * 平面视频（单mp4文件）码率
         */
        public static int getBitRateForPlaneVideo(int videoWidth, int videoHeight, String aspectRatio) {
            int p = Math.min(videoWidth, videoHeight);
            if (aspectRatio.equals("1:1")) {
                switch (p) {
                    case 1920:
                        return 28 * 1024 * 1024;
                    case 1440:
                        return 16 * 1024 * 1024;
                    case 1280:
                        return 12 * 1024 * 1024;
                    case 1080:
                        return 10 * 1024 * 1024;
                    default:
                    case 720:
                        return 4 * 1024 * 1024;
                }
            } else {
                switch (p) {
                    case 1920:
                    case 1440:
                        return 28 * 1024 * 1024;
                    case 1280:
                        return 22 * 1024 * 1024;
                    case 1080:
                        return 16 * 1024 * 1024;
                    default:
                    case 720:
                        return 8 * 1024 * 1024;
                }
            }
        }

        public static int getBitRateForSlowMotionVideo() {
            return 95 * 1024 * 1024;
        }

        /**
         * 延时摄影，（单mp4文件）码率
         */
        public static int getBitRateForTimeLapseVideo() {
            return 120 * 1024 * 1024 / 2;
        }

        /**
         * 街景,计算视频文件内的比特率
         */
        public static int getBitrateForStreetView(float streetBitrateMultiple) {
            return (int) (15 * 1024 * 1024 * streetBitrateMultiple);
        }

        /**
         * 全景Vlog,计算视频文件内的比特率
         */
        public static int getBitrateForVlog() {
            return (int) (30 * 1024 * 1024);
        }

        private static int getTimeLapseRatio(@PiTimeLapseTimes String times) {
            int _times = Integer.parseInt(times);
            int previewFps = PilotSDK.getFps();
            return (int) (_times * (previewFps / 30f));
        }
    }

    /**
     * 获取街景帧率倍率
     */
    public static int getFpsRatioForStreetVideo(int previewFps, @PiStreetVideoFps String streetVideoFps) {
        if (previewFps < 1) {
            throw new RuntimeException("Street Video‘s previewFps is 0");
        }
        if (String.valueOf(previewFps).equals(streetVideoFps)) {
            return 0;
        }
        double _streetVideoFps = Double.parseDouble(streetVideoFps);
        return (int) Math.ceil(previewFps / _streetVideoFps);
    }

    /**
     * 录像过程最短时长，小于此值可能出现无效文件。
     *
     * @param fps 帧率
     * @return 录像过程最短时长（包含本数）。单位秒。
     */
    public static int getStreetVideoMinTime(@PiStreetVideoFps String fps) {
        if (PiFps._0_3.equals(fps)) {
            return 10;
        }
        return 5;
    }
}
