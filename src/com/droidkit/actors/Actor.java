package com.droidkit.actors;

import com.droidkit.actors.mailbox.Mailbox;
import com.droidkit.actors.messages.DeadLetter;
import com.droidkit.actors.messages.NamedMessage;
import com.droidkit.actors.tasks.*;
import com.droidkit.actors.tasks.messages.TaskError;
import com.droidkit.actors.tasks.messages.TaskResult;
import com.droidkit.actors.tasks.messages.TaskTimeout;

import java.lang.reflect.Array;
import java.util.UUID;

/**
 * Actor object
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class Actor {

    private UUID uuid;
    private String path;

    private ActorContext context;
    private Mailbox mailbox;

    private ActorAskImpl askPattern;

    public Actor() {

    }

    /**
     * <p>INTERNAL API</p>
     * Initialization of actor
     *
     * @param uuid    uuid of actor
     * @param path    path of actor
     * @param context context of actor
     * @param mailbox mailbox of actor
     */
    public final void initActor(UUID uuid, String path, ActorContext context, Mailbox mailbox) {
        this.uuid = uuid;
        this.path = path;
        this.context = context;
        this.mailbox = mailbox;
        this.askPattern = new ActorAskImpl(self());
    }

    /**
     * Actor System
     *
     * @return Actor System
     */
    public final ActorSystem system() {
        return context.getSystem();
    }

    /**
     * Self actor reference
     *
     * @return self reference
     */
    public final ActorRef self() {
        return context.getSelf();
    }

    /**
     * Actor context
     *
     * @return context
     */
    protected final ActorContext context() {
        return context;
    }

    /**
     * Sender of last received message
     *
     * @return sender's ActorRef
     */
    public final ActorRef sender() {
        return context.sender();
    }

    /**
     * Actor UUID
     *
     * @return uuid
     */
    protected final UUID getUuid() {
        return uuid;
    }

    /**
     * Actor path
     *
     * @return path
     */
    protected final String getPath() {
        return path;
    }

    /**
     * Actor mailbox
     *
     * @return mailbox
     */
    public final Mailbox getMailbox() {
        return mailbox;
    }

    /**
     * Called before first message receiving
     */
    public void preStart() {

    }

    public final void onReceiveGlobal(Object message) {
        if (message instanceof DeadLetter) {
            if (askPattern.onDeadLetter((DeadLetter) message)) {
                return;
            }
        } else if (message instanceof TaskResult) {
            if (askPattern.onTaskResult((TaskResult) message)) {
                return;
            }
        } else if (message instanceof TaskTimeout) {
            if (askPattern.onTaskTimeout((TaskTimeout) message)) {
                return;
            }
        } else if (message instanceof TaskError) {
            if (askPattern.onTaskError((TaskError) message)) {
                return;
            }
        }
        onReceive(message);
    }

    /**
     * Receiving of message
     *
     * @param message message
     */
    public void onReceive(Object message) {

    }

    /**
     * Called after actor shutdown
     */
    public void postStop() {

    }

    /**
     * Reply message to sender of last message
     *
     * @param message reply message
     */
    public void reply(Object message) {
        if (context.sender() != null) {
            context.sender().send(message, self());
        }
    }

    public AskFuture combine(AskFuture... futures) {
        return askPattern.combine(futures);
    }

    public AskFuture combine(AskCallback<Object[]> callback, AskFuture... futures) {
        AskFuture future = combine(futures);
        future.addListener(callback);
        return future;
    }

    public <T> AskFuture combine(final String name, final Class<T> clazz, AskFuture... futures) {
        return combine(new AskCallback<Object[]>() {
            @Override
            public void onResult(Object[] result) {
                T[] res = (T[]) Array.newInstance(clazz, result.length);
                for (int i = 0; i < result.length; i++) {
                    res[i] = (T) result[i];
                }
                self().send(new NamedMessage(name, res));
            }

            @Override
            public void onError(Throwable throwable) {
                self().send(new NamedMessage(name, throwable));
            }
        }, futures);
    }

    public <T> AskFuture combine(final String name, AskFuture... futures) {
        return combine(new AskCallback<Object[]>() {
            @Override
            public void onResult(Object[] result) {
                self().send(new NamedMessage(name, result));
            }

            @Override
            public void onError(Throwable throwable) {
                self().send(new NamedMessage(name, throwable));
            }
        }, futures);
    }

    /**
     * Ask TaskActor for result
     *
     * @param selection ActorSelection of task
     * @return Future
     */
    public AskFuture ask(ActorSelection selection) {
        return askPattern.ask(system().actorOf(selection), 0, null);
    }

    /**
     * Ask TaskActor for result
     *
     * @param selection ActorSelection of task
     * @param timeout   timeout of task
     * @return Future
     */
    public AskFuture ask(ActorSelection selection, long timeout) {
        return askPattern.ask(system().actorOf(selection), timeout, null);
    }

    /**
     * Ask TaskActor for result
     *
     * @param selection ActorSelection of task
     * @param callback  callback for ask
     * @return Future
     */
    public AskFuture ask(ActorSelection selection, AskCallback callback) {
        return askPattern.ask(system().actorOf(selection), 0, callback);
    }

    /**
     * Ask TaskActor for result
     *
     * @param selection ActorSelection of task
     * @param timeout   timeout of task
     * @param callback  callback for ask
     * @return Future
     */
    public AskFuture ask(ActorSelection selection, long timeout, AskCallback callback) {
        return askPattern.ask(system().actorOf(selection), timeout, callback);
    }

    /**
     * Ask TaskActor for result
     *
     * @param ref ActorRef of task
     * @return Future
     */
    public AskFuture ask(ActorRef ref) {
        return askPattern.ask(ref, 0, null);
    }

    /**
     * Ask TaskActor for result
     *
     * @param ref     ActorRef of task
     * @param timeout timeout of task
     * @return Future
     */
    public AskFuture ask(ActorRef ref, long timeout) {
        return askPattern.ask(ref, timeout, null);
    }

    /**
     * Ask TaskActor for result
     *
     * @param ref      ActorRef of task
     * @param callback callback for ask
     * @return Future
     */
    public AskFuture ask(ActorRef ref, AskCallback callback) {
        return askPattern.ask(ref, 0, callback);
    }

    /**
     * Ask TaskActor for result
     *
     * @param ref      ActorRef of task
     * @param timeout  timeout of task
     * @param callback callback for ask
     * @return Future
     */
    public AskFuture ask(ActorRef ref, long timeout, AskCallback callback) {
        return askPattern.ask(ref, timeout, callback);
    }
}