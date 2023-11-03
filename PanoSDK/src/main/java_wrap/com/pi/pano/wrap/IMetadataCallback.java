package com.pi.pano.wrap;

/**
 * Metadata数据回调接口，sdk内会在拍照录像，拼接时，获取该接口返回数据 写入文件信息中
 */
public interface IMetadataCallback {

    /**
     * @return 当前版本信息
     */
    String getVersion();

    /**
     * @return 当前作者信息
     */
    String getArtist();

}
