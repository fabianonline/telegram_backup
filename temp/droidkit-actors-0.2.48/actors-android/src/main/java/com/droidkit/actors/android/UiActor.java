package com.droidkit.actors.android;

import com.droidkit.actors.*;
import com.droidkit.actors.messages.PoisonPill;

import java.util.UUID;

/**
 * Actor-like object that works in UI Thread and backed by real Actor that dispatched on UI thread too.
 */
public class UiActor {
    private String path;
    private ActorSystem actorSystem;
    private ActorRef actorRef;
    private boolean isKilled;

    public UiActor() {
        this(ActorSystem.system());
    }

    public UiActor(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
        this.actorRef = actorSystem.actorOf(BackedUiActor.createProps(this), "ui_" + UUID.randomUUID());
        this.isKilled = false;
    }

    /**
     * Path of actor
     *
     * @return Path
     */
    public String getPath() {
        return path;
    }

    /**
     * Backed ActorRef
     *
     * @return ActorRef
     */
    public ActorRef getActorRef() {
        return actorRef;
    }

    /**
     * On incoming message
     *
     * @param message message
     */
    public void onReceive(Object message) {

    }

    /**
     * Stop receiving messages by this actor
     */
    public void kill() {
        isKilled = true;
        actorRef.send(PoisonPill.INSTANCE);
    }

    private static class BackedUiActor extends Actor {
        public static Props<BackedUiActor> createProps(final UiActor uiActor) {
            return Props.create(BackedUiActor.class, new ActorCreator<BackedUiActor>() {
                @Override
                public BackedUiActor create() {
                    return new BackedUiActor(uiActor);
                }
            }).changeDispatcher("ui");
        }

        private UiActor uiActor;

        private BackedUiActor(UiActor uiActor) {
            this.uiActor = uiActor;
        }

        @Override
        public void onReceive(Object message) {
            if (!uiActor.isKilled) {
                uiActor.onReceive(message);
            }
        }
    }
}
