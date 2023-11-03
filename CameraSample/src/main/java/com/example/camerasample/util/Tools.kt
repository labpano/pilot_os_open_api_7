package com.example.camerasample.util

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.text.TextUtils
import android.util.Log
import android.util.Size
import java.io.File
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt

const val TAG = "Tools"

fun formatTimeToHHMMSS(second: Int): String? {
    val _second = (second % 60).toLong()
    var _minute = (second / 60).toLong()
    val _hour = _minute / 60
    _minute = _minute % 60
    return String.format(Locale.getDefault(), "%1$02d:%2$02d:%3$02d", _hour, _minute, _second)
}


fun obtainVideoSize(file: File): Size? {
    var size: Size? = null
    try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            val width =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (!TextUtils.isEmpty(width) && !TextUtils.isEmpty(height)) {
                size = Size(width!!.toInt(), height!!.toInt())
            }
        }
    } catch (ex: Exception) {
        Log.e(TAG, "obtainVideoSize,ex:$ex")
        ex.printStackTrace()
    }
    return size
}

fun getCameraCount(file: File): Int {
    var cameraCount = 2
    if (file.name.endsWith("_s.mp4") || file.isFile) {
        //已拼接文件
        cameraCount = 1
    } else {
        val child = File(file, "0.mp4")
        val size = obtainVideoSize(child)
        if (null != size) {
            val isPanoVideo: Boolean = isPanoVideo(size)
            cameraCount = if (isPanoVideo) 1 else 2
        }
    }
    return cameraCount
}

fun isPanoVideo(size: Size): Boolean {
    return if (size.width != 0) {
        size.width / size.height == 2
    } else false
}

fun calculatePlanePushResolution(size: Size, ratio: String): Size {
    val base = min(size.width, size.height)
    ratio.split(":").apply {
        val data = Size(get(0).toInt(), get(1).toInt())
        return if (data.width == data.height) {
            Size(base, base)
        } else if (data.width > data.height) {
            Size(((data.width.toFloat() / data.height) * base).roundToInt(), base)
        } else {
            Size(base, (base / (data.width.toFloat() / data.height)).roundToInt())
        }
    }
}

/**
 * 8k
 */
const val STITCHED_WIDTH_7680 = 7680
const val STITCHED_HEIGHT_3840 = 3840

/**
 * 5.7k
 */
const val STITCHED_WIDTH_5760 = 5760
const val STITCHED_HEIGHT_2880 = 2880

/**
 * 4k
 */
const val STITCHED_WIDTH_3840 = 3840
const val STITCHED_HEIGHT_1920 = 1920

/**
 * 8K原始鱼眼宽
 */
const val FISH_WIDTH_3856 = 3856
const val WIDTH_2_5K_C = 2440
const val WIDTH_2_5K = 2560
const val HEIGHT_2_5K = 1280
const val WIDTH_4K_C = 3864
const val WIDTH_4K = 3840
const val WIDTH_5_7K_C = 5800
const val WIDTH_5_7K = 5760

fun obtainStitchVideoSize(tw: Int, th: Int, fps: Int): IntArray {
    var fps = fps
    val width: Int
    val height: Int
    when (tw) {
        WIDTH_5_7K_C, WIDTH_5_7K -> {
            width = STITCHED_WIDTH_5760
            height = STITCHED_HEIGHT_2880
        }
        WIDTH_4K_C, WIDTH_4K -> {
            width = STITCHED_WIDTH_3840
            height = STITCHED_HEIGHT_1920
        }
        WIDTH_2_5K_C, WIDTH_2_5K -> {
            width = WIDTH_2_5K
            height = HEIGHT_2_5K
        }
        else -> {
            width = tw
            height = th
        }
    }
    return intArrayOf(width, height, fps)
}

fun parseVideoFps(file: File): Int {
    val extractor = MediaExtractor()
    try {
        val path = if (file.isFile && file.name.endsWith("0.mp4")) file.absolutePath
        else File(file, "0.mp4").absolutePath
        extractor.setDataSource(path)
        var avc: String? = null
        var fps = 0
        for (i in 0 until extractor.trackCount) {
            val trackFormat: MediaFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime!!.startsWith("video/avc")) {
                fps = trackFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
            } else if (mime.startsWith("video/hevc")) {
                fps = trackFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
            } else if (mime.startsWith("video/")) {
                fps = trackFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
                avc = "H.264"
            }
            if (null != avc) {
                break
            }
        }
        return fps
    } finally {
        extractor.release()
    }
}


fun formatBitrate(bitrate: Long): String {
    return if (bitrate < 1024 * 1024) {
        val value: Long = (bitrate / 1024)
        String.format(Locale.getDefault(), "%d Kbps", value)
    } else {
        val value: Long = (bitrate / (1024 * 1024))
        String.format(Locale.getDefault(), "%d Mbps", value)
    }
}

/**
 * 删除目录
 *
 * @return `true`: 删除成功 ，`false`: 删除失败
 */
fun File.deleteDir(): Boolean {
    if (!exists()) return true
    if (!isDirectory) return false
    // 现在文件存在且是文件夹
    listFiles()?.forEach {
        if (it.isFile) {
            if (!it.delete()) return false
        } else if (it.isDirectory) {
            if (!it.deleteDir()) return false
        }
    }
    return delete()
}