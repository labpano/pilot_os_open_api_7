package com.pi.pano;

import androidx.annotation.Keep;

@Keep
public class StitchingOpticalFlow {

    static {
        System.loadLibrary(Config.PIPANO_SO_NAME);
    }

    /**
     * jpeg文件是否使用光流拼接
     *
     * @param filename jpeg文件名
     */
    public static native boolean getUseOpticalFlow(String filename);

    /**
     * 拼接jpeg鱼眼文件
     *
     * @param unstitchedFilename  未拼接文件名
     * @param stitchedFilename    要输出的拼接后文件名
     * @param useOpticalFlow      是否使用光流拼接
     * @param lensProtectedEnable 是否使用保护镜
     * @param quality             jpeg质量,范围是0~100
     * @param outWidth            输出jpeg宽度, 传0为默认宽度
     * @param outHeight           输入jpeg高度, 传0为默认高度
     */
    public static native void stitchJpegFile(String unstitchedFilename, String stitchedFilename,
                                             boolean useOpticalFlow, boolean lensProtectedEnable, int quality,
                                             int outWidth, int outHeight);


    /**
     * 拼接jpeg鱼眼文件
     *
     * @param unstitchedFilename 未拼接文件名
     * @param stitchedFilename   要输出的拼接后文件名
     * @param useOpticalFlow     是否使用光流拼接
     * @param quality            jpeg质量,范围是0~100
     * @param outWidth           输出jpeg宽度, 传0为默认宽度
     * @param outHeight          输入jpeg高度, 传0为默认高度
     */
    public static void stitchJpegFile(String unstitchedFilename, String stitchedFilename,
                                      boolean useOpticalFlow, int quality, int outWidth, int outHeight) {
        stitchJpegFile(unstitchedFilename, stitchedFilename, useOpticalFlow, false, quality, outWidth, outHeight);
    }
}
