package com.droidkit.actors.tasks;

import com.droidkit.actors.ActorRef;
import com.droidkit.actors.messages.DeadLetter;
import com.droidkit.actors.tasks.messages.*;

import java.util.HashMap;

/**
 * Implementation of Ask pattern
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class ActorAskImpl {

    private HashMap<Integer, AskContainer> asks = new HashMap<Integer, AskContainer>();
    private int nextReqId = 1;
    private ActorRef self;

    public ActorAskImpl(ActorRef self) {
        this.self = self;
    }

    public <T> AskFuture<T[]> combine(AskFuture... futures) {
        final AskFuture resultFuture = new AskFuture(this, 0);
        final CombineContainer container = new CombineContainer(futures.length);
        for (int i = 0; i < futures.length; i++) {
            final int index = i;
            container.futures[index] = futures[index];
            container.callbacks[index] = new AskCallback() {
                @Override
                public void onResult(Object result) {
                    container.completed[index] = true;
                    container.results[index] = result;
                    boolean isCompleted = true;
                    for (boolean c : container.completed) {
                        if (!c) {
                            isCompleted = false;
                            break;
                        }
                    }

                    if (isCompleted && !container.isCompleted) {
                        container.isCompleted = true;
                        for (int i = 0; i < container.futures.length; i++) {
                            container.futures[i].removeListener(container.callbacks[i]);
                        }
                        resultFuture.onResult(container.results);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    if (!container.isCompleted) {
                        container.isCompleted = true;
                        for (int i = 0; i < container.futures.length; i++) {
                            container.futures[i].removeListener(container.callbacks[i]);
                            container.futures[i].cancel();
                        }
                        resultFuture.onError(throwable);
                    }

                }
            };
            container.futures[index].addListener(container.callbacks[index]);
        }
        return resultFuture;
    }

    public <T> AskFuture<T> ask(ActorRef ref, long timeout, AskCallback<T> callback) {
        int reqId = nextReqId++;
        AskFuture<T> future = new AskFuture<T>(this, reqId);
        if (callback != null) {
            future.addListener(callback);
        }
        AskContainer container = new AskContainer(future, ref, reqId);
        asks.put(reqId, container);
        ref.send(new TaskRequest(reqId), self);
        if (timeout > 0) {
            self.send(new TaskTimeout(reqId), timeout);
        }
        return future;
    }

    public boolean onTaskResult(TaskResult result) {
        AskContainer container = asks.remove(result.getRequestId());
        if (container != null) {
            container.future.onResult(result.getRes());
            return true;
        }

        return false;
    }

    public boolean onTaskError(TaskError error) {
        AskContainer container = asks.remove(error.getRequestId());
        if (container != null) {
            container.future.onError(error.getThrowable());
            return true;
        }

        return false;
    }

    public boolean onTaskTimeout(TaskTimeout taskTimeout) {
        AskContainer container = asks.remove(taskTimeout.getRequestId());
        if (container != null) {
            container.future.onTimeout();
            return true;
        }

        return false;
    }

    public boolean onTaskCancelled(int reqId) {
        AskContainer container = asks.remove(reqId);
        if (container != null) {
            container.ref.send(new TaskCancel(reqId), self);
            return true;
        }

        return false;
    }

    public boolean onDeadLetter(DeadLetter letter) {
        if (letter.getMessage() instanceof TaskRequest) {
            TaskRequest request = (TaskRequest) letter.getMessage();
            AskContainer container = asks.remove(request.getRequestId());
            if (container != null) {
                // Mimic dead letter with timeout exception
                container.future.onError(new AskTimeoutException());
                return true;
            }
        }

        return false;
    }

    private class AskContainer {
        public final AskFuture future;
        public final ActorRef ref;
        public final int requestId;

        private AskContainer(AskFuture future, ActorRef ref, int requestId) {
            this.future = future;
            this.ref = ref;
            this.requestId = requestId;
        }
    }

    private class CombineContainer {
        public boolean isCompleted = false;
        public Object[] results;
        public boolean[] completed;
        public AskFuture[] futures;
        public AskCallback[] callbacks;

        public CombineContainer(int count) {
            results = new Object[count];
            completed = new boolean[count];
            callbacks = new AskCallback[count];
            futures = new AskFuture[count];
        }
    }
}
