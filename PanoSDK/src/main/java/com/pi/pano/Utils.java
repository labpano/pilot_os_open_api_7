package com.pi.pano;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();

    /**
     * 获取未拼接视频文件的基本文件名
     */
    static String getVideoUnstitchFileSimpleName(File file) {
        String fileName = file.getName();
        int index = fileName.lastIndexOf("_u");
        if (index >= 0) {
            fileName = fileName.substring(0, index);
        }
        return fileName;
    }

    static String readContentSingleLine(String path) {
        String result = "";
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            result = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeIO(reader);
        }
        return result;
    }

    static String readContent(String path) {
        BufferedReader reader = null;
        try {
            FileInputStream inputStream = new FileInputStream(path);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            reader = new BufferedReader(inputStreamReader);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
            reader.close();
            return sb.toString().trim();
        } catch (IOException ignore) {
        } finally {
            closeIO(reader);
        }
        return "";
    }

    static void modifyFile(String path, String content) {
        BufferedWriter writer = null;
        try {
            FileWriter fileWriter = new FileWriter(path, false);
            writer = new BufferedWriter(fileWriter);
            writer.append(content);
            writer.flush();
            writer.close();
        } catch (Exception ignore) {
        } finally {
            closeIO(writer);
        }
    }

    static void closeIO(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void onCallbackSuccess(PiCallback callback) {
        if (callback != null) {
            callback.onSuccess();
        }
    }

    static void printCameraParams(CameraCharacteristics characteristics) {
        Log.i(TAG, "mCameraCharacteristics==================" + characteristics);
        List<CaptureRequest.Key<?>> availableCaptureRequestKeys = characteristics.getAvailableCaptureRequestKeys();
        Log.i(TAG, "mCameraCharacteristics=======1===========" + availableCaptureRequestKeys);
        if (availableCaptureRequestKeys != null) {
            availableCaptureRequestKeys.forEach(key -> Log.d(TAG, "availableCaptureRequestKeys===============>" + key));
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            List<CaptureRequest.Key<?>> availableSessionKeys = characteristics.getAvailableSessionKeys();
            if (availableSessionKeys != null) {
                availableSessionKeys.forEach(key -> Log.i(TAG, "availableSessionKeys==================" + key));
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Set<String> physicalCameraIds = characteristics.getPhysicalCameraIds();
            if (physicalCameraIds != null) {
                physicalCameraIds.forEach(key -> Log.w(TAG, "physicalCameraIds==================" + key));
            }
        }
        List<CaptureResult.Key<?>> availableCaptureResultKeys = characteristics.getAvailableCaptureResultKeys();
        if (availableCaptureResultKeys != null) {
            availableCaptureResultKeys.forEach(key -> Log.e(TAG, "availableCaptureResultKeys==================" + key));
        }
        StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (streamConfigurationMap != null) {
            Size[] outputSizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
            if (outputSizes != null) {
                for (Size outputSize : outputSizes) {
                    Log.i(TAG, "SurfaceTexture==outputSizes=================" + outputSize);
                }
            }
            Size[] outputSizes2 = streamConfigurationMap.getOutputSizes(ImageReader.class);
            if (outputSizes2 != null) {
                for (Size outputSize : outputSizes2) {
                    Log.i(TAG, "ImageReader==outputSizes2=================" + outputSize);
                }
            }
            Size[] outputSizes3 = streamConfigurationMap.getOutputSizes(MediaRecorder.class);
            if (outputSizes3 != null) {
                for (Size outputSize : outputSizes3) {
                    Log.i(TAG, "MediaRecorder==outputSizes3=================" + outputSize);
                }
            }

            Size[] outputSizes4 = streamConfigurationMap.getOutputSizes(MediaCodec.class);
            if (outputSizes4 != null) {
                for (Size outputSize : outputSizes4) {
                    Log.i(TAG, "MediaCodec==outputSizes4=================" + outputSize);
                }
            }
            int[] inputFormats = streamConfigurationMap.getInputFormats();
            if (inputFormats != null) {
                for (int i = 0; i < inputFormats.length; i++) {
                    Log.w(TAG, "getInputFormats===================" + inputFormats[i]);
                    Size[] inputSizes = streamConfigurationMap.getInputSizes(inputFormats[i]);
                    for (int j = 0; j < inputSizes.length; j++) {
                        Log.e(TAG, "getInputFormats=======inputSizes============" + inputSizes[j]);
                    }
                }
            }
            int[] outputFormats = streamConfigurationMap.getOutputFormats();
            if (outputFormats != null) {
                for (int i = 0; i < outputFormats.length; i++) {
                    Log.i(TAG, "getOutputFormats===================" + outputFormats[i]);
                }
            }
            Size[] outputSizes1 = streamConfigurationMap.getOutputSizes(ImageFormat.RAW_SENSOR);
            if (outputSizes1 != null) {
                for (int i = 0; i < outputSizes1.length; i++) {
                    Log.i(TAG, "RAW_SENSOR=========getOutputSizes==========" + outputSizes1[i]);
                }
            }
            Size[] outputSizes31 = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            if (outputSizes31 != null) {
                for (int i = 0; i < outputSizes31.length; i++) {
                    Log.i(TAG, "JPEG=====getOutputSizes==============" + outputSizes31[i]);
                }
            }
        }

        Range<Integer> control_ae_compensation_range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        Log.i(TAG, "control_ae_compensation_range：" + control_ae_compensation_range);
        Rational control_ae_compensation_step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        Log.i(TAG, "control_ae_compensation_step：" + control_ae_compensation_step);
    }

    static Size getVideoSize(String filePath) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(filePath);
            String width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            return new Size(Integer.parseInt(width), Integer.parseInt(height));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mmr.release();
        }
        return null;
    }

    public static ComponentName topActivity(Context context) {
        Object service = context.getSystemService(Context.ACTIVITY_SERVICE);
        if (service instanceof ActivityManager) {
            ActivityManager am = (ActivityManager) service;
            List<ActivityManager.RunningTaskInfo> runningTasks = am.getRunningTasks(1);
            if (runningTasks != null && !runningTasks.isEmpty()) {
                return runningTasks.get(0).topActivity;
            }
        }
        return null;
    }

    public static String topActivityName(Context context) {
        ComponentName componentName = topActivity(context);
        return componentName == null ? null : componentName.getClassName();
    }


}
