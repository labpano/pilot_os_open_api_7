package com.pi.pano;

public abstract class ThumbMakerListener {
    /**
     * 要制作缩略图的媒体文件,支持单流已拼接或未拼接jpg或视频文件夹,双流视频文件夹
     */
    public String mMediaFilename;

    /**
     * 要输出缩略图的路径,直接jpg格式
     */
    public String mThumbFilename;

    /**
     * 输出缩略图宽
     */
    public int mThumbWidth;

    /**
     * 输出缩略图高
     */
    public int mThumbHeight;

    /**
     * 输入媒体数量,0-单流已拼接,1-单流未拼接,2-双流未拼接
     */
    public int mCameraCount;

    protected void onMakeThumb(ThumbMaker thumbMaker, String thumbFilename) {
        thumbMaker.release();
    }
}
