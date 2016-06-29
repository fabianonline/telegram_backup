package com.droidkit.actors.sample;

import android.util.Log;
import com.droidkit.actors.Actor;

/**
 * Created by ex3ndr on 14.08.14.
 */
public class LogActor extends Actor {

    @Override
    public void preStart() {
        Log.d("LOGACTOR#" + hashCode(), "preStart");
    }

    @Override
    public void onReceive(Object message) {
        Log.d("LOGACTOR#" + hashCode(), message + "");
    }

    @Override
    public void postStop() {
        Log.d("LOGACTOR#" + hashCode(), "postStop");
    }
}
