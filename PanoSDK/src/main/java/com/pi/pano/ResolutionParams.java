package com.pi.pano;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pano.annotation.PiCameraId;
import com.pi.pano.annotation.PiPhotoResolution;
import com.pi.pano.annotation.PiResolution;
import com.pi.pano.annotation.PiVideoResolution;
import com.pi.pano.wrap.ProParams;
import com.pi.pano.wrap.annotation.PiFps;

import java.util.Arrays;

/**
 * 分辨率参数
 */
public final class ResolutionParams {

    /**
     * 分辨率、帧率，及镜头id
     * {width, height, fps, camera_id} 或 {width, height, fps}
     */
    public int[] resolutions;

    /**
     * 强制切换分辨率
     */
    public boolean forceChange = false;
    public ProParams mProParams;

    private ResolutionParams(int[] resolutions) {
        this.resolutions = resolutions;
    }

    private ResolutionParams(int[] resolutions, ProParams proParams) {
        this.resolutions = resolutions;
        mProParams = proParams;
    }

    @NonNull
    @Override
    public String toString() {
        return "{" +
                "resolutions=" + Arrays.toString(resolutions) +
                '}';
    }

    /**
     * 参数创建
     */
    public static final class Factory {

        /**
         * 构建 拍照分辨率
         */
        public static ResolutionParams createParamsForPhoto(@PiPhotoResolution String resolution,
                                                            @Nullable ProParams params) {
            int[] resolutions;
            switch (resolution) {
                case PiResolution._12K:
                case PiResolution._5_7K:
                    resolutions = CameraPreview.CAMERA_PREVIEW_3868_1934_30;
                    break;
                default:
                    throw new RuntimeException("Photo don't support resolution:" + resolution);
            }
            return new ResolutionParams(new int[]{resolutions[0], resolutions[1], resolutions[2], 2}, params);
        }


        /**
         * 构建 未拼接视频 分辨率
         */
        @NonNull
        public static ResolutionParams createParamsForUnStitchVideo(@PiVideoResolution String resolution, String fps, ProParams proParams) {
            // 摄像头使用单镜头*2
            int[] resolutions = null;
            switch (resolution) {
                case PiResolution._8K:
                    if (PiFps._10.equals(fps)) {
//                        resolutions = CameraPreview.CAMERA_PREVIEW_980_980_10_v;
                        resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    }
                    break;
                case PiResolution._5_7K:
                    if (PiFps._30.equals(fps)) {
                        resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    }
                    break;
                case PiResolution._4K:
                    if (PiFps._60.equals(fps)) {
                        //resolutions = CameraPreview.CAMERA_PREVIEW_940_940_60_v;
                        resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    }
                    break;
                case PiResolution._2_5K:
                    if (PiFps._120.equals(fps)) {
                        //resolutions = CameraPreview.CAMERA_PREVIEW_920_920_120_v;
                        resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    }
                    break;
            }
            if (null == resolutions) {
                throw new RuntimeException("UnStitchVideo don't support resolution:" + resolution + ",fps:" + fps);
            }
            int[] params = new int[resolutions.length + 1];
            params[resolutions.length] = PiCameraId.ASYNC_DOUBLE;
            System.arraycopy(resolutions, 0, params, 0, resolutions.length);
            return new ResolutionParams(params, proParams);
        }

        /**
         * 构建 平面视频 分辨率
         */
        public static ResolutionParams createParamsForPlaneVideo(
                boolean isMainCamera, @PiVideoResolution String resolution, String fps, ProParams proParams) {
            // 摄像头使用单镜头
            int[] resolutions;
            switch (fps) {
                case PiFps._60:
                    resolutions = CameraPreview.CAMERA_PREVIEW_1932_1932_60;
                    break;
                case PiFps._30:
                    resolutions = CameraPreview.CAMERA_PREVIEW_2900_2900_30;
                    break;
                default:
                    throw new RuntimeException("PlaneVideo don't support resolution:" + resolution + ",fps:" + fps);
            }
            int[] params = new int[resolutions.length + 1];
            params[resolutions.length] = isMainCamera ? 0 : 1;
            System.arraycopy(resolutions, 0, params, 0, resolutions.length);
            return new ResolutionParams(params, proParams);
        }


        /**
         * 构建 慢动作 分辨率
         */
        public static ResolutionParams createParamsForSlowMotionVideo(ProParams proParams) {
            // 摄像头使用单镜头*2
            int[] resolutions;
//            resolutions = CameraPreview.CAMERA_PREVIEW_920_920_120_v;
            resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
            int[] params = new int[4];
            params[resolutions.length] = PiCameraId.ASYNC_DOUBLE;
            System.arraycopy(resolutions, 0, params, 0, resolutions.length);
            return new ResolutionParams(params, proParams);
        }

        /**
         * 构建 （延时摄影）分辨率
         */
        public static ResolutionParams createParamsForTimeLapseVideo(@PiVideoResolution String resolution, ProParams proParams) {
            // 摄像头使用单镜头*2,但通过pano sdk 出mp4。
            int[] resolutions;
            switch (resolution) {
                case PiResolution._8K:
                    resolutions = CameraPreview.CAMERA_PREVIEW_3868_3868_10;
//                    resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    break;
                case PiResolution._5_7K:
                    resolutions = CameraPreview.CAMERA_PREVIEW_2900_2900_30;
//                    resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    break;
                default:
                    throw new RuntimeException("TimeLapseVideo don't support resolution:" + resolution);
            }
            int[] params = new int[4];
            params[resolutions.length] = PiCameraId.ASYNC_DOUBLE;
            System.arraycopy(resolutions, 0, params, 0, resolutions.length);
            return new ResolutionParams(params, proParams);
        }


        /**
         * 构建 录像（街景）分辨率
         */
        public static ResolutionParams createParamsForStreetViewVideo(@PiVideoResolution String resolution, @PiFps String fps, ProParams proParams) {
            // 摄像头使用单镜头*2,但通过pano sdk 出mp4。
            int[] resolutions = null;
            switch (resolution) {
                case PiResolution._8K:
                    if (PiFps._5.equals(fps) || PiFps._2.equals(fps) || PiFps._1.equals(fps)) {
                        resolutions = CameraPreview.CAMERA_PREVIEW_3868_3868_10;
//                        resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    }
                    break;
                case PiResolution._5_7K:
                    if (PiFps._7.equals(fps) || PiFps._2.equals(fps) || PiFps._1.equals(fps)) {
                        resolutions = CameraPreview.CAMERA_PREVIEW_2900_2900_14;
//                        resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    } else if (PiFps._4.equals(fps)) {
                        resolutions = CameraPreview.CAMERA_PREVIEW_2900_2900_16;
//                        resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    } else if (PiFps._3.equals(fps)) {
                        resolutions = CameraPreview.CAMERA_PREVIEW_2900_2900_15;
//                        resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    }
                    break;
            }
            if (resolutions == null) {
                throw new RuntimeException("StreetViewVideo don't support:" + resolution + ",fps:" + fps);
            }
            int[] params = new int[resolutions.length + 1];
            params[resolutions.length] = PiCameraId.ASYNC_DOUBLE;
            System.arraycopy(resolutions, 0, params, 0, resolutions.length);
            return new ResolutionParams(params, proParams);
        }


        /**
         * 构建 录像（Vlog视频）分辨率
         */
        public static ResolutionParams createParamsForVlogVideo(ProParams proParams) {
            int[] resolutions = CameraPreview.CAMERA_PREVIEW_2900_2900_30;
            int[] params = new int[resolutions.length + 1];
            params[resolutions.length] = PiCameraId.ASYNC_DOUBLE;
            System.arraycopy(resolutions, 0, params, 0, resolutions.length);
            return new ResolutionParams(params, proParams);
        }


        /**
         * 构建 录像（智能跟拍）分辨率
         */
        public static ResolutionParams createParamsForScreenVideo(ProParams proParams) {
            int[] resolutions = CameraPreview.CAMERA_PREVIEW_2900_2900_30;
            int[] params = new int[resolutions.length + 1];
            params[resolutions.length] = PiCameraId.ASYNC_DOUBLE;
            System.arraycopy(resolutions, 0, params, 0, resolutions.length);
            return new ResolutionParams(params, proParams);
        }

        /**
         * 构建 直播分辨率参数
         */
        public static ResolutionParams createParamsForPanoramaLive(String fps, ProParams proParams) {
            // 摄像头使用单镜头*2,但通过pano sdk 出图。
            int[] resolutions;
            switch (fps) {
                case PiFps._30:
//                    resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    resolutions = CameraPreview.CAMERA_PREVIEW_2900_2900_30;
                    break;
                default:
                    throw new RuntimeException("PanoramaLive don't fps:" + fps);
            }
            int[] params = new int[resolutions.length + 1];
            params[resolutions.length] = PiCameraId.ASYNC_DOUBLE;
            System.arraycopy(resolutions, 0, params, 0, resolutions.length);
            return new ResolutionParams(params, proParams);
        }

        /**
         * 构建 屏幕直播分辨率
         */
        public static ResolutionParams createParamsForScreenLive(String fps, ProParams proParams) {
            // 摄像头使用单镜头*2,但通过pano sdk 出图。
            int[] resolutions;
            switch (fps) {
                case PiFps._30:
//                    resolutions = CameraPreview.CAMERA_PREVIEW_960_960_30_v;
                    resolutions = CameraPreview.CAMERA_PREVIEW_2900_2900_30;
                    break;
                default:
                    throw new RuntimeException("PanoramaLive don't fps:" + fps);
            }
            int[] params = new int[resolutions.length + 1];
            params[resolutions.length] = PiCameraId.ASYNC_DOUBLE;
            System.arraycopy(resolutions, 0, params, 0, resolutions.length);
            return new ResolutionParams(params, proParams);
        }
    }
}
