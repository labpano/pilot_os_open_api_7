package com.pi.pano.annotation;

import androidx.annotation.StringDef;

/**
 * 文件或文件夹名称的拼接标识。
 */
@StringDef({
        PiFileStitchFlag.unstitch,
        PiFileStitchFlag.stitch,
        PiFileStitchFlag.none
})
public @interface PiFileStitchFlag {
    /**
     * 未拼接
     */
    String unstitch = "_u";
    /**
     * 已拼接
     */
    String stitch = "_s";
    /**
     * 无需拼接
     */
    String none = "_n";
}
