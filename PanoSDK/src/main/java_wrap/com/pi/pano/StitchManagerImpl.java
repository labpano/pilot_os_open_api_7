package com.pi.pano;

import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiFileStitchFlag;
import com.pi.pano.annotation.PiResolution;
import com.pi.pano.wrap.IMetadataCallback;
import com.pi.pano.wrap.PhotoStitchWrap;

import java.io.File;

/**
 * 拼接管理实现
 */
public class StitchManagerImpl implements IStitchManager {

    private static final String TAG = "stitchManager";
    private final StitchingUtil mStitchUtil;

    public StitchManagerImpl() {
        mStitchUtil = new StitchingUtil(PilotLib.self().getContext());
    }

    @Override
    public String stitchPhoto(@NonNull PhotoStitchWrap.PhotoStitchParams params) {
        if (TextUtils.isEmpty(params.targetFilePath)) {
            params.targetFilePath = createStitchFilePath(params.srcFilePath);
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

    @Override
    public void setVideoStitchListener(StitchingListener listener) {
        mStitchUtil.setStitchingListener(listener);
    }

    @Override
    public boolean addVideoTask(@NonNull StitchVideoParams params) {
        IMetadataCallback metadataCallback = PilotLib.self().getMetadataCallback();
        if (metadataCallback != null) {
            StitchingUtil.mArtist = metadataCallback.getArtist();
            StitchingUtil.mFirmware = metadataCallback.getVersion();
        }
        return mStitchUtil.addStitchTask(params);
    }

    @Override
    public void startVideoStitchTask() {
        mStitchUtil.startAllStitchTask();
    }

    @Override
    public void pauseVideoStitchTask() {
        mStitchUtil.pauseAllStitchTask();
    }

    @Override
    public void deleteVideoStitchTask() {
        mStitchUtil.deleteAllStitchTask();
    }

    //region private

    /**
     * 生成已拼接文件路径
     *
     * @param unStitchFilePath 未拼接文件路径
     * @return 已拼接路径
     */
    private String createStitchFilePath(String unStitchFilePath) {
        int lastIndexOf;
        lastIndexOf = unStitchFilePath.lastIndexOf(PiFileStitchFlag.unstitch + "_hdr.jpg");
        if (lastIndexOf > 0) {
            return unStitchFilePath.substring(0, lastIndexOf) + PiFileStitchFlag.stitch + "_hdr.jpg";
        }
        lastIndexOf = unStitchFilePath.lastIndexOf(PiFileStitchFlag.unstitch + ".jpg");
        if (lastIndexOf > 0) {
            return unStitchFilePath.substring(0, lastIndexOf) + PiFileStitchFlag.stitch + ".jpg";
        }
        lastIndexOf = unStitchFilePath.lastIndexOf(".jpg");
        if (lastIndexOf > 0) {
            return unStitchFilePath.substring(0, lastIndexOf) + PiFileStitchFlag.stitch + ".jpg";
        }
        return unStitchFilePath;
    }
    //endregion


}
