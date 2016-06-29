package org.telegram.mtproto.schedule;

/**
 * Created by ex3ndr on 29.12.13.
 */
public class PrepareSchedule {
    private long delay;
    private int[] allowedContexts;
    private boolean doWait;

    public boolean isDoWait() {
        return doWait;
    }

    public void setDoWait(boolean doWait) {
        this.doWait = doWait;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public int[] getAllowedContexts() {
        return allowedContexts;
    }

    public void setAllowedContexts(int[] allowedContexts) {
        this.allowedContexts = allowedContexts;
    }
}
