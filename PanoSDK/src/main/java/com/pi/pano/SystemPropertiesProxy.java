package com.pi.pano;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;

/**
 * 系统属性代理
 */
public final class SystemPropertiesProxy {
    private static final String TAG = "sysProp";

    public static String get(@NonNull String key) {
        try {
            @SuppressLint("PrivateApi") Class<?> c = obtainSystemPropertiesClass();
            Method get = c.getMethod("get", String.class);
            return (String) get.invoke(c, key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String get(@NonNull String key, @Nullable String def) {
        try {
            @SuppressLint("PrivateApi") Class<?> c = obtainSystemPropertiesClass();
            Method get = c.getMethod("get", String.class, String.class);
            return (String) get.invoke(c, key, def);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return def;
    }

    public static int getInt(@NonNull String key, int def) {
        try {
            @SuppressLint("PrivateApi") Class<?> c = obtainSystemPropertiesClass();
            Method get = c.getMethod("getInt", String.class, int.class);
            return (int) get.invoke(c, key, def);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return def;
    }

    public static long getLong(@NonNull String key, long def) {
        try {
            @SuppressLint("PrivateApi") Class<?> c = obtainSystemPropertiesClass();
            Method get = c.getMethod("getLong", String.class, long.class);
            return (long) get.invoke(c, key, def);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return def;
    }

    public static boolean getBoolean(@NonNull String key, boolean def) {
        try {
            @SuppressLint("PrivateApi") Class<?> c = obtainSystemPropertiesClass();
            Method get = c.getMethod("getBoolean", String.class, boolean.class);
            return (boolean) get.invoke(c, key, def);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return def;
    }

    public static void set(@NonNull String key, @Nullable String val) {
        try {
            Class<?> c = obtainSystemPropertiesClass();
            Method get = c.getMethod("set", String.class, String.class);
            get.invoke(c, key, val);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static <T> void set(@NonNull String key, @Nullable T val) {
        String valStr = (null == val ? null : String.valueOf(val));
        set(key, valStr);
    }

    public static float getFloat(@NonNull String key, float def) {
        String valStr = get(key);
        if (valStr == null || valStr.isEmpty()) {
            return def;
        }
        return Float.parseFloat(valStr);
    }

    public static double getDouble(@NonNull String key, double def) {
        String valStr = get(key);
        if (valStr == null || valStr.isEmpty()) {
            return def;
        }
        return Double.parseDouble(valStr);
    }

    @SuppressLint("PrivateApi")
    @NonNull
    public static Class<?> obtainSystemPropertiesClass() throws ClassNotFoundException {
        return Class.forName("android.os.SystemProperties");
    }

    public static Object parseDebugPropData(String keyName) {
        File propFile = getPropFile();
        String content = Utils.readContent(propFile.getAbsolutePath());
        if (!TextUtils.isEmpty(content)) {
            try {
                JSONObject jsonObject = new JSONObject(content);
                Object data = jsonObject.get(keyName);
                Log.d(TAG, "parse json :" + content + ", key :" + keyName);
                return data;
            } catch (Exception e) {
                Log.e(TAG, "parse json error :" + e.getMessage() + ",from ==> " + content);
            }
        }
        return null;
    }

    private static File getPropFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), ".temp");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, "panoDev.prop");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        return file;
    }
}
