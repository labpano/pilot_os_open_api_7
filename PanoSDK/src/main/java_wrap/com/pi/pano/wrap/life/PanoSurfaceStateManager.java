package com.pi.pano.wrap.life;

import android.util.Log;

import androidx.annotation.IntDef;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * surface创建销毁时序状态管理
 */
public class PanoSurfaceStateManager {
    private static final String TAG = "panoSurfaceState";
    private final AtomicInteger mState;

    public PanoSurfaceStateManager() {
        mState = new AtomicInteger(State.INITIALIZED);
    }

    public void changeState(@State int newState) {
        Log.d(TAG, "changeState : " + mState.get() + " to ==>" + newState);
        mState.set(newState);
    }

    @State
    public int getState() {
        return mState.get();
    }

    public boolean isCreateScope() {
        return inCreateLifeScope(mState.get());
    }

    public boolean isDestroyScope() {
        return inDestroyScope(mState.get());
    }

    public boolean inCreateLifeScope(@State int state) {
        return state >= State.CREATE_START && state <= State.CREATE_FINISHED;
    }

    public boolean inDestroyScope(@State int state) {
        return state >= State.DESTROY_START && state <= State.DESTROY_FINISHED;
    }

    @IntDef({
            State.INITIALIZED,
            State.CREATE_START, State.CREATE_ING, State.CREATE_FINISHED,
            State.DESTROY_START, State.DESTROY_ING, State.DESTROY_FINISHED,
    })
    public @interface State {
        int INITIALIZED = -1;

        int CREATE_START = 0;
        int CREATE_ING = 1;
        int CREATE_FINISHED = 2;

        int DESTROY_START = 10;
        int DESTROY_ING = 11;
        int DESTROY_FINISHED = 12;
    }

}
