package com.droidkit.actors.dispatch;

/**
 * Queue for dispatching messages for {@link ThreadPoolDispatcher}.
 * Implementation MUST BE thread-safe.
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public abstract class AbstractDispatchQueue<T> {

    /**
     * Value used for result of waitDelay when dispatcher need to wait forever
     */
    protected static final long FOREVER = Long.MAX_VALUE;

    private QueueListener listener;

    /**
     * Fetch message for dispatching and removing it from dispatch queue
     *
     * @param time current time from ActorTime
     * @return message or null if there is no message for processing
     */
    public abstract T dispatch(long time);

    /**
     * Expected delay for nearest message.
     * You might provide most accurate value as you can,
     * this will minimize unnecessary thread work.
     * For example, if you will return zero here then thread will
     * loop continuously and consume processor time.
     *
     * @param time current time from ActorTime
     * @return delay in ms
     */
    public abstract long waitDelay(long time);

    /**
     * Implementation of adding message to queue
     *
     * @param message
     * @param atTime
     */
    protected abstract void putToQueueImpl(T message, long atTime);

    /**
     * Adding message to queue
     *
     * @param message message
     * @param atTime  time (use {@link com.droidkit.actors.ActorTime#currentTime()} for currentTime)
     */
    public final void putToQueue(T message, long atTime) {
        putToQueueImpl(message, atTime);
        notifyQueueChanged();
    }

    /**
     * Notification about queue change.
     */
    protected void notifyQueueChanged() {
        QueueListener lListener = listener;
        if (lListener != null) {
            lListener.onQueueChanged();
        }
    }

    /**
     * Getting of current queue listener
     *
     * @return queue listener
     */
    public QueueListener getListener() {
        return listener;
    }

    /**
     * Setting queue listener
     *
     * @param listener queue listener
     */
    public void setListener(QueueListener listener) {
        this.listener = listener;
    }
}