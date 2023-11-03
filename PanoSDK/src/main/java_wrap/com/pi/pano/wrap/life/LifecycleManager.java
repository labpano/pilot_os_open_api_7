package com.pi.pano.wrap.life;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.pi.pano.Utils;

import java.lang.ref.WeakReference;

/**
 * 生命周期管理
 */
public final class LifecycleManager {

    private static final String TAG = "LifecycleManager";
    private final WeakReference<Activity> mAttachActivity;
    private boolean mFilterByStart;
    /**
     * 当前绑定的activity 经过异常检测过滤后的 生命周期状态
     */
    private State mSafeState;
    /**
     * 当前绑定的activity生命周期状态
     */
    private State mCurState;
    private final Application.ActivityLifecycleCallbacks mLifecycleCallbacks
            = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            Log.d(TAG, "onActivityCreated :" + activity + ", top==> " + Utils.topActivityName(activity));
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            mCurState = State.STARTED;
            if (!filterByTopActivity(activity)) {
                Log.i(TAG, "onActivityStarted :" + activity);
                onStart();
            } else {
                Log.w(TAG, "onActivityStarted filter :" + activity);
                mFilterByStart = true;
            }
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            mCurState = State.RESUMED;
            if (!filterByTopActivity(activity) && !mFilterByStart) {
                Log.i(TAG, "onActivityResumed :" + activity);
                onResume();
            } else {
                Log.w(TAG, "onActivityResumed filter :" + activity + ",mFilterByStart:" + mFilterByStart);
            }
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            mCurState = State.PAUSED;
            Log.i(TAG, "onActivityPaused :" + activity);
            onPause();
            mFilterByStart = false;
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            mCurState = State.STOPPED;
            Log.i(TAG, "onActivityStopped :" + activity);
            onStop();
            mFilterByStart = false;
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            mCurState = State.DESTROYED;
            Log.d(TAG, "onActivityDestroyed :" + activity + ", top==> " + Utils.topActivityName(activity));
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public LifecycleManager(@NonNull Activity activity) {
        mSafeState = State.INITIALIZED;
        mAttachActivity = new WeakReference<>(activity);
        Log.d(TAG, "init :" + mLifecycleCallbacks);
        activity.unregisterActivityLifecycleCallbacks(mLifecycleCallbacks);
        activity.registerActivityLifecycleCallbacks(mLifecycleCallbacks);
    }

    public LifecycleManager.State getCurrentState() {
        return mCurState;
    }

    private boolean filterByTopActivity(Activity activity) {
        String topName = Utils.topActivityName(activity);
        String curName = activity.getComponentName().getClassName();
        Log.d(TAG, "cur :" + curName + ", top: " + topName);
        return !TextUtils.equals(curName, topName);
    }

    private void onStart() {
        mSafeState = State.STARTED;
    }

    private void onResume() {
        mSafeState = State.RESUMED;
    }

    private void onPause() {
        mSafeState = State.PAUSED;
    }

    private void onStop() {
        mSafeState = State.STOPPED;
    }

    public void release() {
        Activity activity = mAttachActivity.get();
        Log.d(TAG, "release :" + activity + ",," + mLifecycleCallbacks);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && activity != null) {
            activity.unregisterActivityLifecycleCallbacks(mLifecycleCallbacks);
        }
        mAttachActivity.clear();
    }

    public boolean isActive() {
        return isActive(mSafeState);
    }

    public boolean isInActive() {
        return isInActive(mSafeState);
    }

    public boolean isInActive(State state) {
        return state == State.PAUSED ||
                state == State.STOPPED ||
                state == State.DESTROYED;
    }

    public boolean isActive(State state) {
        return state == State.STARTED ||
                state == State.RESUMED;
    }

    public enum State {
        INITIALIZED,
        STARTED,
        RESUMED,
        PAUSED,
        STOPPED,
        DESTROYED,
    }

}
