package com.pi.pano.annotation;

import androidx.annotation.StringDef;

@StringDef({
        PiPhotoFileFormat.jpg,
        PiPhotoFileFormat.jpg_dng,
        PiPhotoFileFormat.jpg_raw
})
public @interface PiPhotoFileFormat {
    String jpg = "jpg";
    @Deprecated
    String raw = "raw";
    @Deprecated
    String jpg_dng = "jpg+dng";
    @Deprecated
    String jpg_raw = "jpg+raw";
}
