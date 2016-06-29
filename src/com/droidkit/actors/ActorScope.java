package com.droidkit.actors;

import com.droidkit.actors.mailbox.AbsActorDispatcher;
import com.droidkit.actors.mailbox.Mailbox;

import java.util.UUID;

/**
 * <p>INTERNAL API</p>
 * Actor Scope contains states of actor, UUID, Path, Props and Actor (if created).
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class ActorScope {

    public static final int STATE_STARTING = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_SHUTDOWN = 2;

    private final UUID uuid;
    private final String path;
    private final Props props;

    private final ActorRef actorRef;
    private final Mailbox mailbox;

    private final AbsActorDispatcher dispatcher;

    private final ActorSystem actorSystem;

    private int state;

    private Actor actor;

    private ActorRef sender;

    public ActorScope(ActorSystem actorSystem, Mailbox mailbox, ActorRef actorRef, AbsActorDispatcher dispatcher, UUID uuid, String path, Props props) {
        this.actorSystem = actorSystem;
        this.mailbox = mailbox;
        this.actorRef = actorRef;
        this.dispatcher = dispatcher;
        this.uuid = uuid;
        this.path = path;
        this.props = props;
        this.state = STATE_STARTING;
    }

    public AbsActorDispatcher getDispatcher() {
        return dispatcher;
    }

    public int getState() {
        return state;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPath() {
        return path;
    }

    public Props getProps() {
        return props;
    }

    public ActorRef getActorRef() {
        return actorRef;
    }

    public Mailbox getMailbox() {
        return mailbox;
    }

    public Actor getActor() {
        return actor;
    }

    public ActorSystem getActorSystem() {
        return actorSystem;
    }

    public ActorRef getSender() {
        return sender;
    }

    public void setSender(ActorRef sender) {
        this.sender = sender;
    }

    /**
     * Create actor
     *
     * @throws Exception
     */
    public void createActor() throws Exception {
        if (state == STATE_STARTING) {
            actor = props.create();
            CurrentActor.setCurrentActor(actor);
            actor.initActor(getUuid(), getPath(), new ActorContext(this), getMailbox());
            actor.preStart();
        } else if (state == STATE_RUNNING) {
            throw new RuntimeException("Actor already created");
        } else if (state == STATE_SHUTDOWN) {
            throw new RuntimeException("Actor shutdown");
        } else {
            throw new RuntimeException("Unknown ActorScope state");
        }
    }

    /**
     * Shutdown actor
     *
     * @throws Exception
     */
    public void shutdownActor() throws Exception {
        if (state == STATE_STARTING || state == STATE_RUNNING ||
                state == STATE_SHUTDOWN) {
            actorSystem.removeActor(this);
            dispatcher.disconnectScope(this);
            actor.postStop();
            actor = null;
        } else {
            throw new RuntimeException("Unknown ActorScope state");
        }
    }
}
