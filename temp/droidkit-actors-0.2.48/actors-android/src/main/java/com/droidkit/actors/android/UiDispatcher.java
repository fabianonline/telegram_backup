package com.droidkit.actors.android;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import com.droidkit.actors.ActorTime;
import com.droidkit.actors.dispatch.AbstractDispatchQueue;
import com.droidkit.actors.dispatch.AbstractDispatcher;
import com.droidkit.actors.dispatch.Dispatch;

/**
 * Thread Dispatcher that dispatches messages on UI Thread
 */
public class UiDispatcher<T, Q extends AbstractDispatchQueue<T>> extends AbstractDispatcher<T, Q> {
    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void dispatchMessage(Message msg) {
            doIteration();
        }
    };

    protected UiDispatcher(Q queue, Dispatch<T> dispatch) {
        super(queue, dispatch);
    }

    protected void invalidate() {
        handler.removeMessages(0);
        handler.sendEmptyMessage(0);
    }

    protected void invalidateDelay(long delay) {
        handler.removeMessages(0);
        if (delay > 15000) {
            delay = 15000;
        }
        handler.sendEmptyMessageDelayed(0, delay);
    }

    @Override
    protected void notifyDispatcher() {
        invalidate();
    }

    protected void doIteration() {
        long time = ActorTime.currentTime();
        T action = getQueue().dispatch(time);
        if (action == null) {
            long delay = getQueue().waitDelay(time);
            invalidateDelay(delay);
        } else {
            dispatchMessage(action);
        }
    }
}