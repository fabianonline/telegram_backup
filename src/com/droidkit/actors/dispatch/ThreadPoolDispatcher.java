package com.droidkit.actors.dispatch;

import static com.droidkit.actors.ActorTime.currentTime;

/**
 * ThreadPoolDispatcher is used for dispatching messages on it's own threads.
 * Class is completely thread-safe.
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class ThreadPoolDispatcher<T, Q extends AbstractDispatchQueue<T>> extends AbstractDispatcher<T, Q> {

    private final Thread[] threads;

    private boolean isClosed = false;

    /**
     * Dispatcher constructor. Create threads with NORM_PRIORITY.
     *
     * @param count    thread count
     * @param queue    queue for messages
     *                 (see {@link com.droidkit.actors.dispatch.AbstractDispatchQueue} for more information)
     * @param dispatch Dispatch for message processing
     */
    public ThreadPoolDispatcher(int count, Q queue, Dispatch<T> dispatch) {
        this(count, Thread.NORM_PRIORITY, queue, dispatch);
    }

    /**
     * Dispatcher constructor. Create threads with NORM_PRIORITY.
     * Should override dispatchMessage for message processing.
     *
     * @param count thread count
     * @param queue queue for messages
     *              (see {@link com.droidkit.actors.dispatch.AbstractDispatchQueue} for more information)
     */
    public ThreadPoolDispatcher(int count, Q queue) {
        this(count, Thread.NORM_PRIORITY, queue, null);
    }

    /**
     * Dispatcher constructor. Create threads with NORM_PRIORITY.
     * Should override dispatchMessage for message processing.
     *
     * @param count    thread count
     * @param priority thread priority
     * @param queue    queue for messages
     *                 (see {@link com.droidkit.actors.dispatch.AbstractDispatchQueue} for more information)
     */
    public ThreadPoolDispatcher(int count, int priority, Q queue) {
        this(count, priority, queue, null);
    }


    /**
     * Dispatcher constructor
     *
     * @param count    thread count
     * @param priority thread priority
     * @param queue    queue for messages
     *                 (see {@link com.droidkit.actors.dispatch.AbstractDispatchQueue} for more information)
     * @param dispatch Dispatch for message processing
     */
    public ThreadPoolDispatcher(int count, int priority, final Q queue, Dispatch<T> dispatch) {
        super(queue, dispatch);

        this.threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            this.threads[i] = new DispatcherThread();
            this.threads[i].setPriority(priority);
            this.threads[i].start();
        }
    }

    /**
     * Closing of dispatcher no one actions will be executed after calling this method.
     */
    public void close() {
        isClosed = true;
        notifyDispatcher();
    }

    /**
     * Notification about queue change
     */
    @Override
    protected void notifyDispatcher() {
        if (threads != null) {
            synchronized (threads) {
                threads.notifyAll();
            }
        }
    }

    /**
     * Thread class for dispatching
     */
    private class DispatcherThread extends Thread {
        @Override
        public void run() {
            while (!isClosed) {
                T action = getQueue().dispatch(currentTime());
                if (action == null) {
                    synchronized (threads) {
                        try {
                            long delay = getQueue().waitDelay(currentTime());
                            if (delay > 0) {
                                threads.wait(delay);
                            }
                            continue;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                }

                try {
                    dispatchMessage(action);
                } catch (Throwable t) {
                    // Possibly danger situation, but i hope this will not corrupt JVM
                    // For example: on Android we could always continue execution after OutOfMemoryError
                    // Anyway, better to catch all errors manually in dispatchMessage
                    t.printStackTrace();
                }
            }
        }
    }
}