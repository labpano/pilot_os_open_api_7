package com.pi.pano;

import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.pi.pano.annotation.PiVideoEncode;

class EncodeConverter {

    @NonNull
    static String convertVideoMime(@PiVideoEncode String encode) {
        switch (encode) {
            case PiVideoEncode.h_265:
                return MediaFormat.MIMETYPE_VIDEO_HEVC;
            default:
            case PiVideoEncode.h_264:
                return MediaFormat.MIMETYPE_VIDEO_AVC;
        }
    }
}
