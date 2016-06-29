package com.droidkit.actors.sample;

import com.droidkit.actors.ActorRef;
import com.droidkit.actors.ActorSystem;

/**
 * Created by ex3ndr on 18.08.14.
 */
public class Log {
    private static final ActorRef log = ActorSystem.system().actorOf(LogActor.class, "log");

    public static void d(String s) {
        log.send(s);
    }
}
