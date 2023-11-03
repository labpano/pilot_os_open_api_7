package com.pi.pano;

import androidx.annotation.NonNull;

/**
 * 分辨率
 */
public final class ResolutionSize {
    public final int width;
    public final int height;

    public ResolutionSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @NonNull
    @Override
    public String toString() {
        return toResolutionSizeString(width, height);
    }

    /**
     * 解析分辨率
     *
     * @param resolution 分辨率参数，支持#号
     */
    @NonNull
    public static ResolutionSize safeParseSize(@NonNull final String resolution) {
        if (resolution.contains("#")) {
            try {
                return parseSize(resolution.substring(0, resolution.indexOf("#")));
            } catch (Exception ex) {
                throw new RuntimeException("error resolution(" + resolution + ")");
            }
        } else {
            return parseSize(resolution);
        }
    }

    /**
     * 解析分辨率
     *
     * @param resolution 分辨率参数，不支持#号
     */
    @NonNull
    public static ResolutionSize parseSize(@NonNull final String resolution) {
        try {
            String[] split = resolution.split("\\*");
            int width = Integer.parseInt(split[0]);
            int height = Integer.parseInt(split[1]);
            return new ResolutionSize(width, height);
        } catch (Exception e) {
            throw new RuntimeException("error resolution(" + resolution + ")");
        }
    }

    /**
     * 转换成的width*height字符串。
     */
    public static String toResolutionSizeString(int width, int height) {
        return width + "*" + height;
    }
}
