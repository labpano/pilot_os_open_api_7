package com.pi.pano;

import android.view.Surface;

import androidx.annotation.Keep;

import java.io.File;

@Keep
class StitchingNative {
    @Keep
    private long mNativeContext;//c++用,不要删除

    native int createNativeObj(String dirname, Surface[] surfaces, boolean forPlayer);
    native int deleteFrameExtractor();
    native void seekToPreviousKeyFrame(long timeStamp);
    native boolean extractorOneFrame();
    native void decodeOneFrame(boolean renderEncodeFrame);
    native long getExtractorSampleTime(int index);

    int startStitching(String filename, PiPano piPano, boolean forPlayer) {
        //寻找参数顺序
        //1.先寻找sti文件夹下有没有param.txt文件,有则用之,注意这个param.txt不是机身自带参数
        //2.看看0.mp4内是否包含参数
        //3.使用机身内参数
        //这个函数同样读取并设置了拼接距离
        File file = new File(filename + "/param.txt");
        if (file.exists()) {
            piPano.setParamByMedia(filename + "/param.txt");
        } else {
            piPano.setParamByMedia(filename + "/0.mp4");
        }

        //video extractor
        Surface[] surfaces = new Surface[piPano.mFrameAvailableTask.length];
        for (int i = 0; i < surfaces.length; ++i) {
            surfaces[i] = new Surface(piPano.mFrameAvailableTask[i].mSurfaceTexture);
        }
        return createNativeObj(filename, surfaces,forPlayer);
    }
}
