package com.droidkit.actors.sample;

import com.droidkit.actors.Actor;

/**
 * Created by ex3ndr on 27.08.14.
 */
public class CounterActor extends Actor {
    int last = -1;

    @Override
    public void onReceive(Object message) {
        if (message instanceof Integer) {
            int val = (Integer) message;
            if (last != val - 1) {
                Log.d("Error! Wrong order expected #" + (last + 1) + " got #" + val);
            }
            last++;
        }
    }
}
