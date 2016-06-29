package org.telegram.mtproto.time;

import org.telegram.mtproto.log.Logger;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 02.11.13
 * Time: 21:35
 */
public class TimeOverlord {
    private static TimeOverlord instance;

    public static synchronized TimeOverlord getInstance() {
        if (instance == null) {
            instance = new TimeOverlord();
        }
        return instance;
    }

    private long nanotimeShift;

    private long timeAccuracy = Long.MAX_VALUE;
    protected long timeDelta;

    private TimeOverlord() {
        nanotimeShift = System.currentTimeMillis() - System.nanoTime() / 1000;
    }

    public long createWeakMessageId() {
        return (getServerTime() / 1000) << 32;
    }

    public long getLocalTime() {
        return System.currentTimeMillis();
    }

    public long getServerTime() {
        return getLocalTime() + timeDelta;
    }

    public long getTimeAccuracy() {
        return timeAccuracy;
    }

    public long getTimeDelta() {
        return timeDelta;
    }

    public void setTimeDelta(long timeDelta, long timeAccuracy) {
        this.timeDelta = timeDelta;
        this.timeAccuracy = timeAccuracy;
    }

    public void onForcedServerTimeArrived(long serverTime, long duration) {
        timeDelta = serverTime - getLocalTime();
        timeAccuracy = duration;
    }

    public void onServerTimeArrived(long serverTime, long duration) {
        if (duration < 0) {
            return;
        }
        if (duration < timeAccuracy) {
            timeDelta = serverTime - getLocalTime();
            timeAccuracy = duration;
        } else if (Math.abs(getLocalTime() - serverTime) > (duration / 2 + timeAccuracy / 2)) {
            timeDelta = serverTime - getLocalTime();
            timeAccuracy = duration;
        }
    }

    public void onMethodExecuted(long sentId, long responseId, long duration) {
        if (duration < 0) {
            return;
        }

        onServerTimeArrived((responseId >> 32) * 1000, duration);
    }
}