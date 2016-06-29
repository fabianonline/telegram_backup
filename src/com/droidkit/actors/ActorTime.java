package com.droidkit.actors;

/**
 * Time used by actor system, uses System.nanoTime() inside
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class ActorTime {

    private static long lastTime = 0;
    private static final Object timeLock = new Object();

    /**
     * Getting current actor system time
     *
     * @return actor system time
     */
    public static long currentTime() {
        long res = System.nanoTime() / 1000000;
        synchronized (timeLock) {
            if (lastTime < res) {
                lastTime = res;
            }
            return lastTime;
        }
    }
}