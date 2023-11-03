package com.example.camerasample.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.StringRes;

import com.example.camerasample.BuildConfig;

public final class ToastUtils {
    private static boolean DEBUG = BuildConfig.DEBUG;

    public static void setDebug(boolean debug) {
        DEBUG = debug;
    }

    public static boolean isDebug() {
        return DEBUG;
    }

    private static final Handler mHandler = new Handler(Looper.getMainLooper());
    private static Toast mToast = null;

    public static void show(Context context, @StringRes int resId) {
        show(context, context.getText(resId), Toast.LENGTH_SHORT);
    }

    public static void show(Context context, CharSequence text) {
        show(context, text, Toast.LENGTH_SHORT);
    }

    public static void show(Context context, @StringRes int resId, int duration) {
        show(context, context.getText(resId), duration);
    }

    public static void show(final Context context, final CharSequence text, final int duration) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        mHandler.post(() -> {
            if (null == mToast) {
                mToast = createToast(appContext);
            }
            mToast.setText(text);
            mToast.setDuration(duration);
            mToast.show();
        });
    }

    private static Toast createToast(Context appContext) {
        Toast toast = Toast.makeText(appContext, null, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        return toast;
    }

}
