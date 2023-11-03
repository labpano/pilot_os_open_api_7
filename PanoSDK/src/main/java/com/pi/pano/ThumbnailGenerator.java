package com.pi.pano;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图片、视频缩略图生成器
 */
public class ThumbnailGenerator {
    private static final String TAG = ThumbnailGenerator.class.getSimpleName();

    /**
     * 缩略图最大字节数
     */
    public static int sMaxSizeByte = 60_000;
    /**
     * 缩略图宽、高
     */
    public static final int[] sThumbnailsSize = new int[]{480, 240};
    public static final int[] sThumbnailsSquareSize = new int[]{480, 480};
    /**
     * 未拼接图提取的缩略图宽、高
     */
    public static final int[] sUnStitchedThumbnailsSize = new int[]{(int) (960 * 1.2), (int) (480 * 1.2)};
    private static final int MAX_TIME_OUT = 4000;

    /**
     * 缩略图处理
     *
     * @param dstFile 文件路径
     * @param isImage true:图片 false:视频
     * @param stitch  true:已拼接 ,false:未拼接
     * @return 返回的缩略图字节数组
     */
    public static byte[] extractThumbnails(@NonNull File dstFile, boolean isImage, boolean stitch) {
        return isImage ? ThumbnailGenerator.extractImageThumbnails(dstFile, stitch)
                : ThumbnailGenerator.extractVideoThumbnails(dstFile, stitch);
    }

    /**
     * 图片缩略图处理
     *
     * @param dstFile 文件路径
     * @return 返回的缩略图字节数组
     */
    public static byte[] extractImageThumbnails(@NonNull File dstFile, boolean stitch) {
        return stitch ? extractImageThumbnailsBytes(dstFile) :
                extractImageThumbnailsBytesByLeftBall(dstFile);
    }

    /**
     * 获取未拼接图片缩略图，使用left球处理。
     *
     * @param dstFile 文件路径
     * @return 返回的缩略图字节数组
     */
    @Nullable
    public static byte[] extractImageThumbnailsBytesByLeftBall(@NonNull File dstFile) {
        final long startTimestamp = System.currentTimeMillis();
        byte[] bytes = null;
        Bitmap bitmap = null;
        try {
            bitmap = extractImageThumbnails(dstFile, sUnStitchedThumbnailsSize);
            if (bitmap != null) {
                bitmap = cropLeftBall(bitmap, sThumbnailsSize);
            }
            if (bitmap != null) {
                bytes = compressToBytes(bitmap, sMaxSizeByte);
            }
        } catch (Exception ex) {
            Log.e(TAG, "extractImageThumbnailsBytesByLeftBall,ex:" + ex);
            ex.printStackTrace();
            if (null != bitmap) {
                bitmap.recycle();
            }
        }
        Log.d(TAG, "extractImageThumbnailsBytesByLeftBall,dstFile:" + dstFile + ",cost time:" + (System.currentTimeMillis() - startTimestamp));
        return bytes;
    }

    /**
     * 获取图片缩略图。
     *
     * @param dstFile 文件路径
     * @return 返回的缩略图字节数组
     */
    @Nullable
    public static byte[] extractImageThumbnailsBytes(@NonNull File dstFile) {
        final long startTimestamp = System.currentTimeMillis();
        byte[] bytes = null;
        Bitmap bitmap = null;
        try {
            bitmap = extractImageThumbnails(dstFile, sThumbnailsSize);
            if (bitmap != null) {
                bytes = compressToBytes(bitmap, sMaxSizeByte);
            }
        } catch (Exception ex) {
            Log.e(TAG, "extractImageThumbnailsBytes,ex:" + ex);
            ex.printStackTrace();
            if (null != bitmap) {
                bitmap.recycle();
            }
        }
        Log.d(TAG, "extractImageThumbnailsBytes,dstFile:" + dstFile + ",cost time:" + (System.currentTimeMillis() - startTimestamp));
        return bytes;
    }

    /**
     * 视频缩略图处理
     *
     * @param dstFile 文件路径
     * @return 返回的缩略图字节数组
     */
    public static byte[] extractVideoThumbnails(@NonNull File dstFile, boolean stitch) {
        return stitch ? extractVideoThumbnailsBytes(dstFile) :
                extractVideoThumbnailsBytesByLeftBall(dstFile);
    }

    /**
     * 获取未拼接视频缩略图，提取第一帧并使用left球处理。
     *
     * @param dstFile 文件路径
     * @return 返回的缩略图字节数组
     */
    @Nullable
    public static byte[] extractVideoThumbnailsBytesByLeftBall(@NonNull File dstFile) {
        final long startTimestamp = System.currentTimeMillis();
        byte[] bytes = null;
        final Bitmap[] bitmap = {null};
        try {
            File parentFile = dstFile.getParentFile();
            File thumbnail = new File(parentFile.getParent(), ".t." + parentFile.getName() + ".jpg");
            int cameraCount = 0;
            if (dstFile.getName().equalsIgnoreCase("0.mp4")) {
                cameraCount = 1;
                File file1 = new File(parentFile, "1.mp4");
                if (file1.exists() && file1.length() > 0) {
                    cameraCount = 2;
                }
            }
            if (cameraCount > 0) {
                final Object lock = new Object();
                doThumbMaker(parentFile, thumbnail, cameraCount, new ThumbMakerListener() {
                    @Override
                    protected void onMakeThumb(ThumbMaker thumbMaker, String thumbFilename) {
                        super.onMakeThumb(thumbMaker, thumbFilename);
                        Log.d(TAG, "onMakeThumb ==>" + thumbFilename);
                        if (!TextUtils.isEmpty(thumbFilename)) {
                            bitmap[0] = BitmapFactory.decodeFile(thumbnail.getAbsolutePath());
                        }
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                        thumbnail.delete();
                    }
                });
                try {
                    synchronized (lock) {
                        lock.wait(MAX_TIME_OUT);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                bitmap[0] = extractVideoThumbnails(dstFile, sUnStitchedThumbnailsSize);
                if (bitmap[0] != null) {
                    bitmap[0] = cropLeftBall(bitmap[0], bitmap[0].getWidth() == bitmap[0].getHeight() ?
                            sThumbnailsSquareSize : sThumbnailsSize);
                }
            }
            if (bitmap[0] != null) {
                bytes = compressToBytes(bitmap[0], sMaxSizeByte);
            }
        } catch (Exception ex) {
            Log.e(TAG, "extractVideoThumbnailsBytesByLeftBall,ex:" + ex);
            ex.printStackTrace();
            if (null != bitmap[0]) {
                bitmap[0].recycle();
            }
        }
        Log.d(TAG, "extractVideoThumbnailsBytesByLeftBall,dstFile:" + dstFile
                + ",cost time:" + (System.currentTimeMillis() - startTimestamp));
        return bytes;
    }

    /**
     * 获取视频缩略图,提取第一帧。
     *
     * @param dstFile 文件路径
     * @return 返回的缩略图字节数组
     */
    @Nullable
    public static byte[] extractVideoThumbnailsBytes(@NonNull File dstFile) {
        final long startTimestamp = System.currentTimeMillis();
        byte[] bytes = null;
        Bitmap[] bitmap = {null};
        try {
            String name = dstFile.getName();
            if (name.endsWith("plv_s.mp4") || name.endsWith("plv_u")) {
                bitmap[0] = extractVideoThumbnails(dstFile, sThumbnailsSize);
            } else {
                File parentFile = dstFile.getParentFile();
                File thumbnail = new File(parentFile, ".t." + name.replace(".mp4", "") + ".jpg");
                final Object lock = new Object();
                doThumbMaker(dstFile, thumbnail, 0, new ThumbMakerListener() {
                    @Override
                    protected void onMakeThumb(ThumbMaker thumbMaker, String thumbFilename) {
                        super.onMakeThumb(thumbMaker, thumbFilename);
                        Log.d(TAG, "onMakeThumb ==>" + thumbFilename);
                        if (!TextUtils.isEmpty(thumbFilename)) {
                            bitmap[0] = BitmapFactory.decodeFile(thumbnail.getAbsolutePath());
                        }
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                        thumbnail.delete();
                    }
                });
                try {
                    synchronized (lock) {
                        lock.wait(MAX_TIME_OUT);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (bitmap[0] != null) {
                bytes = compressToBytes(bitmap[0], sMaxSizeByte);
            }
        } catch (Exception ex) {
            Log.e(TAG, "extractVideoThumbnailsBytes,ex:" + ex);
            ex.printStackTrace();
            if (null != bitmap[0]) {
                bitmap[0].recycle();
            }
        }
        Log.d(TAG, "extractVideoThumbnailsBytes,dstFile:" + dstFile + ",cost time:" + (System.currentTimeMillis() - startTimestamp));
        return bytes;
    }

    /**
     * 提取图片缩略图
     *
     * @param dstSize 指定的宽高
     */
    @Nullable
    private static Bitmap extractImageThumbnails(@NonNull File dstFile, @NonNull int[] dstSize) {
        String filepath = dstFile.getAbsolutePath();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filepath, options);
        //计算压缩比
        int h = options.outHeight;
        int w = options.outWidth;
        options.inSampleSize = Math.min(h / dstSize[1], w / dstSize[0]);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filepath, options);
    }

    /**
     * 提取视频缩略图
     *
     * @param dstSize 指定的宽高
     */
    @Nullable
    private static Bitmap extractVideoThumbnails(@NonNull File dstFile, @NonNull int[] dstSize) {
        String filepath = dstFile.getAbsolutePath();
        Bitmap ret = null;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(filepath);
            ret = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_NEXT_SYNC);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mmr.release();
        }
        if (ret != null) {
            ret = android.media.ThumbnailUtils.extractThumbnail(ret,
                    dstSize[0], ret.getWidth() == ret.getHeight() ? dstSize[0] : dstSize[1],
                    android.media.ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        }
        return ret;
    }

    /**
     * 裁切未拼接图像中left球,并压缩到指定宽高。
     *
     * @param dstSize 指定的宽高
     */
    @Nullable
    private static Bitmap cropLeftBall(@NonNull Bitmap origin, @NonNull int[] dstSize) {
        Bitmap bitmap = origin.getWidth() == origin.getHeight() ?
                cropSingleBall(origin) : cropLeftBall(origin);
        if (bitmap != null) {
            int height = bitmap.getHeight();
            int width = bitmap.getWidth();
            float scaleWidth = ((float) dstSize[0]) / width;
            float scaleHeight = ((float) dstSize[1]) / height;
            Matrix matrix = new Matrix();
            matrix.postScale(scaleWidth, scaleHeight);// 使用后乘
            Bitmap replace = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
            bitmap.recycle();
            return replace;
        }
        return null;
    }

    /**
     * 裁切未拼接图像中left球
     * <p>
     * 计算方式：需要导出缩率图的宽高比为2:1，
     * 首先先计算出第一个镜头中圆的半径min(width/4,height/2)
     * 然后根据半径计算出圆心的坐标左上角的x坐标为（1-2*Math.sqrt(5) / 5）*radius,y的坐标应为（1-Math.sqrt(5) / 5）
     * 按照此时的剪切方式，四个角会出现锯齿和黑边，则将将左上角坐标像右下方向移动一些:
     * 即x = ((1 - Math.sqrt(6) / 3) * radius),y = ((1 - Math.sqrt(6) / 6) * radius)
     */
    @Nullable
    private static Bitmap cropLeftBall(@NonNull Bitmap origin) {
        int radius = Math.min(origin.getWidth() / 4, origin.getHeight() / 2);
        int x = (int) ((1 - Math.sqrt(6) / 3) * radius);
        int y = (int) ((1 - Math.sqrt(6) / 6) * radius);
        int corpWidth = (int) ((2 * Math.sqrt(6) / 3) * radius);
        int corpHeight = (int) ((Math.sqrt(6) / 3) * radius);
        Bitmap replace = Bitmap.createBitmap(origin, x, y, corpWidth, corpHeight, null, false);
        origin.recycle();
        return replace;
    }

    /**
     * 从单鱼眼文件中 截取出内切正方形
     *
     * @param origin 单鱼眼文件bitmap
     */
    private static Bitmap cropSingleBall(@NonNull Bitmap origin) {
        int width = origin.getWidth();
        int radius = width / 2;
        //内切正方形边长
        int squareSize = (int) Math.sqrt(radius * radius / 2f) * 2;
        int x = (width - squareSize) / 2;
        //坐标向下偏移,宽高减小一些，，避免裁剪的图有锯齿和黑边
        x += 5;
        int cropSize = squareSize - 10;
        Bitmap replace = Bitmap.createBitmap(origin, x, x, cropSize, cropSize, null, false);
        origin.recycle();
        return replace;
    }

    /**
     * 压缩到指定的大小
     */
    @NonNull
    private static byte[] compressToBytes(@NonNull Bitmap origin, int maxSizeByte) {
        byte[] ret;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = 100;
        do {
            origin.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            ret = baos.toByteArray();
            baos.reset();
        } while (ret.length > maxSizeByte && (quality -= 10) > 0);
        origin.recycle();
        return ret;
    }

    private static int extractImageThumbnailsByMaker(@NonNull File file, @NonNull File thumbFile) {
        final int[] ret = new int[]{-1};
        final Object lock = new Object();
        ThumbMakerListener listener = new ThumbMakerListener() {
            @Override
            protected void onMakeThumb(ThumbMaker thumbMaker, String thumbFilename) {
                super.onMakeThumb(thumbMaker, thumbFilename);
                Log.d(TAG, "onMakeThumb ====> " + thumbFilename);
                if (!TextUtils.isEmpty(thumbFilename)) {
                    ret[0] = PiPanoUtils.injectThumbnail(file.getAbsoluteFile(), thumbFile.getAbsoluteFile());
                }
                thumbFile.delete();
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };
        doThumbMaker(file, thumbFile, 1, listener);
        try {
            synchronized (lock) {
                lock.wait(MAX_TIME_OUT);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "extractImageThumbnailsByMaker " + file + ",time out:" + e.getMessage());
        }
        return ret[0];
    }

    /**
     * 照片 exif 内注入缩略图
     *
     * @param imageFile jpg文件
     */
    public static int injectExifThumbnailForImage(@NonNull File imageFile) {
        Log.d(TAG, "injectExifThumbnailForImage :" + imageFile + ",," + Thread.currentThread().getName());
        final long startTimestamp = System.currentTimeMillis();
        File thumbnail = new File(imageFile.getParentFile(), ".t." + imageFile.getName());
        int ret = extractImageThumbnailsByMaker(imageFile, thumbnail);
        Log.d(TAG, "injectExifThumbnailForImage,imageFile:" + imageFile
                + ",cost time:" + (System.currentTimeMillis() - startTimestamp) + ",ret:" + ret);
        return ret;
    }

    public static File createImageThumbFile(@NonNull File srcFile) {
        Log.d(TAG, "createThumbFile :" + srcFile + ",," + Thread.currentThread().getName());
        final long startTimestamp = System.currentTimeMillis();
        File thumbnail = new File(srcFile.getParentFile(), ".t." + srcFile.getName());
        final Object lock = new Object();
        final File[] thumbResult = {null};
        ThumbMakerListener listener = new ThumbMakerListener() {
            @Override
            protected void onMakeThumb(ThumbMaker thumbMaker, String thumbFilename) {
                super.onMakeThumb(thumbMaker, thumbFilename);
                Log.d(TAG, "onMakeThumb ====> " + thumbFilename);
                if (!TextUtils.isEmpty(thumbFilename)) {
                    thumbResult[0] = new File(thumbFilename);
                }
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        };
        doThumbMaker(srcFile, thumbnail, 1, listener);
        try {
            synchronized (lock) {
                lock.wait(MAX_TIME_OUT);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "createThumbFile " + srcFile + ",time out:" + e.getMessage());
        }
        Log.d(TAG, "createThumbFile :" + srcFile
                + ",cost time:" + (System.currentTimeMillis() - startTimestamp)
                + ",result:" + thumbResult[0]);
        return thumbResult[0];
    }

    public static AtomicInteger sCount = new AtomicInteger();

    private static void doThumbMaker(File file, File thumbnail, int cameraCount, ThumbMakerListener listener) {
        Log.d(TAG, "doThumbMaker :" + file + ",," + thumbnail);
        HandlerThread thread = new HandlerThread("thumb-create-" + sCount.incrementAndGet());
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        handler.post(() -> {
            ThumbMaker thumbMaker = new ThumbMaker(handler);
            listener.mMediaFilename = file.getAbsolutePath();
            listener.mThumbFilename = thumbnail.getAbsolutePath();
            listener.mThumbWidth = 240;
            listener.mThumbHeight = 240;
            listener.mCameraCount = cameraCount;
            int result = thumbMaker.makeThumbnail(listener);
            Log.d(TAG, "doThumbMaker success :" + (result == 0));
            if (result != 0) {
                listener.onMakeThumb(thumbMaker, null);
            }
        });
    }

}
