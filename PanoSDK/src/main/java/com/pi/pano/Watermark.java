package com.pi.pano;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pi.pilot.pano.sdk.R;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 水印即底部logo设置
 */
public final class Watermark {
    private static final String TAG = Watermark.class.getSimpleName();

    /**
     * 无水印名称，即不使用水印
     */
    public static final String NAME_NONE = "null";
    /**
     * 默认水印名称
     */
    public static final String NAME_DEFAULT = "default";

    /**
     * 水印设置文件
     */
    private static final String WATERMARKS_SETTING_PATH = "/sdcard/Watermarks/setting";

    /**
     * 名称最大长度，
     * 第1个字节记录有名称的实际字节数-length,
     * 根据length读取后续字节数（utf-8编码）
     */
    private static final int NAME_MAX_LENGTH = 252;

    /**
     * size长度
     */
    private static final int SIZE_LENGTH = 4;
    /**
     * 总的可用长度，256个字节。
     */
    private static final int TOTAL_LENGTH = NAME_MAX_LENGTH + SIZE_LENGTH;

    private Watermark() {
    }

    /**
     * 获取水印配置文件。
     * 水印目录不存在时会生成目录
     */
    @NonNull
    private static File obtainSettingFile() {
        File settingFile = new File(WATERMARKS_SETTING_PATH);
        File parentFile = settingFile.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        return settingFile;
    }

    /**
     * 检查水印配置文件。
     * 不存在时是否释放默认水印
     */
    public static void checkWatermark(Context context) {
        File settingFile = obtainSettingFile();
        if (!settingFile.exists()) {
            Log.w(TAG, "watermark setting file is not exists,reset to default");
            resetDefaultWatermark(context);
        }
    }

    /**
     * 重置水印为默认
     *
     * @return 水印名称, 返回空时写入失败
     */
    @Nullable
    public static String resetDefaultWatermark(Context context) {
        InputStream inputStream = getDefaultWatermarkStream(context);
        return writeInfo(NAME_DEFAULT, inputStream);
    }

    /**
     * 获取默认水印流
     */
    @NonNull
    public static InputStream getDefaultWatermarkStream(Context context) {
        return context.getResources().openRawResource(R.raw.watermark);
    }

    /**
     * 是否使用水印
     *
     * @return true:使用水印
     */
    public static boolean isWatermarkAble() {
        return !NAME_NONE.equals(getWatermarkName());
    }

    /**
     * 设置不使用水印
     *
     * @return 水印名称, 返回空时写入失败
     */
    @Nullable
    public static String disableWatermark() {
        return writeInfo(NAME_NONE, null);
    }

    /**
     * 是否使用默认水印
     */
    public static boolean isDefaultWatermark() {
        return NAME_DEFAULT.equals(getWatermarkName());
    }

    /**
     * 改变水印大小
     *
     * @return -1:失败
     */
    public static int changeWaterMarkSize(@IntRange(from = 0, to = 26) int size) {
        return writeInfo(size);
    }

    /**
     * 设置水印.
     * 水印文件为空、不存在或不可用时，将使用默认水印。
     *
     * @param context 上下文,当file不可用时重置水印
     * @param file    使用的水印文件
     * @return 水印名称, 返回空时写入失败
     */
    @Nullable
    public static String enableWatermark(@NonNull Context context, @Nullable File file) {
        String name = null;
        if (null != file && file.isFile()) {
            name = enableWatermark(file);
        }
        if (null == name) {
            name = resetDefaultWatermark(context);
        }
        return name;
    }

    /**
     * 设置水印
     *
     * @param file 使用的水印文件
     * @return 水印名称, 返回空时写入失败
     */
    @Nullable
    public static String enableWatermark(@NonNull File file) {
        if (!file.isFile()) {
            return null;
        }
        FileInputStream inputStream;
        try {
            inputStream = new FileInputStream(file);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "setWatermark,file:" + file + ",ex:" + ex);
            return null;
        }
        return writeInfo(file.getName(), inputStream);
    }

    /**
     * 设置水印
     *
     * @param name        水印名称
     * @param inputStream 水印内容流
     * @return 水印名称, 返回空时写入失败
     */
    @Nullable
    public static String enableWatermark(@NonNull String name, @Nullable InputStream inputStream) {
        if (NAME_NONE.equals(name)) {
            inputStream = null;
        }
        return writeInfo(name, inputStream);
    }

    /**
     * @return 水印名称
     */
    @NonNull
    public static String getWatermarkName() {
        String name = NAME_NONE;
        byte[] buffer = readHead();
        if (buffer != null) {
            final int firstPosition = 0;
            int length = Math.min(0xFF & buffer[firstPosition], NAME_MAX_LENGTH - 1);
            if (length > 0) {
                name = new String(buffer, firstPosition + 1, length);
            }
        }
        return name;
    }

    /**
     * 获取大小
     */
    public static int getWaterMarkSize() {
        int size = 0;
        byte[] buffer = readHead();
        if (buffer != null) {
            final int firstPosition = NAME_MAX_LENGTH;
            size = bytesToInt(buffer, firstPosition);
        }
        return size;
    }

    /**
     * 获取水印内容
     */
    public static InputStream getWaterMarkStream() {
        return readWatermarkStream();
    }

    /**
     * 读取全部头字节
     *
     * @return 文件不存在或长度不对时为空
     */
    @Nullable
    private static byte[] readHead() {
        File setting = obtainSettingFile();
        if (setting.isFile() && setting.length() >= TOTAL_LENGTH) {
            FileInputStream is = null;
            try {
                is = new FileInputStream(setting);
                byte[] buffer = new byte[TOTAL_LENGTH];
                if (is.read(buffer) == TOTAL_LENGTH) {
                    return buffer;
                }
            } catch (IOException ex) {
                Log.e(TAG, "readHead,ex:" + ex);
                ex.printStackTrace();
            } finally {
                closeIO(is);
            }
        }
        return null;
    }

    /**
     * 读取水印内容
     */
    @Nullable
    private static InputStream readWatermarkStream() {
        File setting = obtainSettingFile();
        if (setting.exists() && setting.length() > TOTAL_LENGTH) {
            FileInputStream is = null;
            try {
                is = new FileInputStream(setting);
                is.skip(TOTAL_LENGTH);
            } catch (Exception ex) {
                Log.e(TAG, "readWatermarkStream,ex:" + ex);
                ex.printStackTrace();
                closeIO(is);
            }
        }
        return null;
    }

    /**
     * 写入size
     */
    private static int writeInfo(int size) {
        try {
            writeInfo(size, null, null);
            return size;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    /**
     * 写入水印信息
     *
     * @param name        水印名称
     * @param inputStream 水印内容，png
     * @return 水印名称, 返回空时写入失败
     */
    @Nullable
    private static String writeInfo(@NonNull String name, @Nullable InputStream inputStream) {
        try {
            return writeInfo(-1, name, inputStream);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            closeIO(inputStream);
        }
        return null;
    }

    /**
     * 写入信息
     *
     * @param size        水印大小，为-1是不更新
     * @param name        水印名称，为null时不更新
     * @param inputStream 水印内容
     * @return 水印名称，一般不为空
     */
    @Nullable
    private static String writeInfo(int size, @Nullable String name, @Nullable InputStream inputStream) throws IOException {
        byte[] buffer = new byte[TOTAL_LENGTH];

        File setting = obtainSettingFile();
        FileInputStream is_setting = null;
        if (setting.isFile() && setting.length() >= TOTAL_LENGTH) {
            is_setting = new FileInputStream(setting);
            is_setting.read(buffer);
        }

        File temp = new File(setting.getParentFile(), "." + setting.getName());
        FileOutputStream os_temp = new FileOutputStream(temp);

        int position = 0;
        InputStream copy_is;

        // 名称
        if (null != name) {
            position += writeName(buffer, position, name);
            copy_is = inputStream;
        } else {
            if (is_setting != null) {
                position += writeName(buffer, position, null);
                copy_is = is_setting;
                name = "";
            } else {
                // 无水印
                name = NAME_NONE;
                position += writeName(buffer, position, name);
                copy_is = null;
            }
        }

        // 大小
        position += writeSize(buffer, position, size);

        os_temp.write(buffer); // 写入head
        if (null != copy_is) {
            copyStream(os_temp, copy_is); // 写入水印内容
        }

        if (temp.exists()) {
            if (!setting.exists() && !setting.exists()) {
                Log.d(TAG, "write info,old file delete failed!");
            }
            if (!temp.renameTo(setting)) {
                Log.d(TAG, "write info,temp rename failed!");
                name = null;
            }
        }
        return name;
    }

    private static int writeName(byte[] buffer, int position, String name) {
        if (null != name) {
            byte[] bytes = name.getBytes();
            int length = Math.min(bytes.length, NAME_MAX_LENGTH - 1);
            buffer[position++] = (byte) length;
            System.arraycopy(bytes, 0, buffer, position, length);
            // 兼容c字符串,结尾为0
            position += length;
            buffer[position] = 0;
        }
        return NAME_MAX_LENGTH;
    }

    private static int writeSize(byte[] buffer, int position, int size) {
        if (size >= 0) {
            byte[] bytes = intToBytes(size);
            System.arraycopy(bytes, 0, buffer, position, SIZE_LENGTH);
        }
        return SIZE_LENGTH;
    }

    /**
     * 检测是否是水印图片
     *
     * @return true:水印合法可用
     */
    public static boolean checkLegalWatermark(@NonNull File file) {
        if (file.isFile() && file.length() <= 1024 * 1024 && isPNG(file)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            return opts.outWidth == 512 && opts.outHeight == 512;
        }
        return false;
    }

    /**
     * 判断是否为png类型
     */
    private static boolean isPNG(@NonNull File file) {
        byte[] png_header = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        // 读取文件前4个字节
        byte[] header = null;
        try (FileInputStream is = new FileInputStream(file)) {
            header = new byte[4];
            is.read(header, 0, header.length);
        } catch (Exception ignored) {
        }
        if (null != header) {
            for (int i = 0; i < 4; i++) {
                if (png_header[i] != header[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 计算水印覆盖大小（最大长度，单位:米）。
     */
    public static double calculateWaterMarkSize(int size) {
        float degree = 180.0f * (size + 4) / 100;
        return Math.tan(Math.toRadians(degree)) * 1.135 * 2;
    }

    // region other methods

    private static void closeIO(Closeable... io) {
        if (null != io) {
            for (Closeable i : io) {
                try {
                    i.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void copyStream(FileOutputStream os, @Nullable InputStream is) throws IOException {
        byte[] buffer;
        // 写入水印内容
        if (null != is) {
            buffer = new byte[1024 * 100];
            int i;
            while ((i = is.read(buffer)) != -1) {
                os.write(buffer, 0, i);
            }
        }
        //
        os.flush();
    }

    private static byte[] intToBytes(int value) {
        byte[] bytes = new byte[4];
        bytes[3] = (byte) ((value & 0xFF000000) >> 24);
        bytes[2] = (byte) ((value & 0x00FF0000) >> 16);
        bytes[1] = (byte) ((value & 0x0000FF00) >> 8);
        bytes[0] = (byte) ((value & 0x000000FF));
        return bytes;
    }

    private static int bytesToInt(byte[] bytes, int offset) {
        return (int) ((bytes[offset])
                | ((bytes[offset + 1] << 8))
                | ((bytes[offset + 2] << 16))
                | ((bytes[offset + 3] << 24)));
    }

    // endregion
}
