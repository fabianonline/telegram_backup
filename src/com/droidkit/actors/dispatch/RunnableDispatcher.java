package com.droidkit.actors.dispatch;

import static com.droidkit.actors.ActorTime.currentTime;

/**
 * RunnableDispatcher is used for executing various Runnable in background
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class RunnableDispatcher extends ThreadPoolDispatcher<Runnable, SimpleDispatchQueue<Runnable>> {

    /**
     * Creating of dispatcher with one thread
     */
    public RunnableDispatcher() {
        this(1);
    }

    /**
     * Creating of dispatcher with {@code threadsCount} threads
     *
     * @param threadsCount number of threads
     */
    public RunnableDispatcher(int threadsCount) {
        super(threadsCount, new SimpleDispatchQueue<Runnable>());
    }

    /**
     * Creating of dispatcher with {@code threadsCount} threads and {@code priority}
     *
     * @param threadsCount number of threads
     * @param priority     priority of threads
     */
    public RunnableDispatcher(int threadsCount, int priority) {
        super(threadsCount, priority, new SimpleDispatchQueue<Runnable>());
    }

    @Override
    protected void dispatchMessage(Runnable object) {
        object.run();
    }

    /**
     * Post action to queue
     *
     * @param action action
     */
    public void postAction(Runnable action) {
        postAction(action, 0);
    }

    /**
     * Post action to queue with delay
     *
     * @param action action
     * @param delay  delay
     */
    public void postAction(Runnable action, long delay) {
        getQueue().putToQueue(action, currentTime() + delay);
    }

    /**
     * Removing action from queue
     *
     * @param action action
     */
    public void removeAction(Runnable action) {
        getQueue().removeFromQueue(action);
    }
}