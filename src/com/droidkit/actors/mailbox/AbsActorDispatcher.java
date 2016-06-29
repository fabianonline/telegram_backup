package com.droidkit.actors.mailbox;

import com.droidkit.actors.*;
import com.droidkit.actors.dispatch.AbstractDispatcher;
import com.droidkit.actors.messages.DeadLetter;
import com.droidkit.actors.messages.PoisonPill;
import com.droidkit.actors.messages.StartActor;

import java.util.HashMap;
import java.util.UUID;

/**
 * Abstract Actor Dispatcher, used for dispatching messages for actors
 */
public abstract class AbsActorDispatcher {

    private final HashMap<Mailbox, ActorScope> mailboxes = new HashMap<Mailbox, ActorScope>();
    private final HashMap<String, ActorScope> scopes = new HashMap<String, ActorScope>();
    private final HashMap<String, Props> actorProps = new HashMap<String, Props>();

    private final ActorSystem actorSystem;
    private AbstractDispatcher<Envelope, MailboxesQueue> dispatcher;

    public AbsActorDispatcher(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    protected void initDispatcher(AbstractDispatcher<Envelope, MailboxesQueue> dispatcher) {
        if (this.dispatcher != null) {
            throw new RuntimeException("Double dispatcher init");
        }
        this.dispatcher = dispatcher;
    }

    public final ActorScope createScope(String path, Props props) {
        // TODO: add path check

        Mailbox mailbox = new Mailbox(dispatcher.getQueue());
        UUID uuid = UUID.randomUUID();
        ActorRef ref = new ActorRef(actorSystem, this, uuid, path);
        ActorScope scope = new ActorScope(actorSystem, mailbox, ref, this, UUID.randomUUID(), path, props);

        synchronized (mailboxes) {
            mailboxes.put(mailbox, scope);
            scopes.put(scope.getPath(), scope);
            actorProps.put(path, props);
        }

        // Sending init message
        scope.getActorRef().send(StartActor.INSTANCE);
        return scope;
    }

    public final void disconnectScope(ActorScope scope) {
        synchronized (mailboxes) {
            mailboxes.remove(scope.getMailbox());
            scopes.remove(scope.getPath());
        }
        for (Envelope envelope : scope.getMailbox().allEnvelopes()) {
            if (envelope.getSender() != null) {
                envelope.getSender().send(new DeadLetter(envelope.getMessage()));
            }
        }
    }

    public final void sendMessage(String path, Object message, long time, ActorRef sender) {
        synchronized (mailboxes) {
            if (!scopes.containsKey(path)) {
                if (sender != null) {
                    sender.send(new DeadLetter(message));
                }
            } else {
                Mailbox mailbox = scopes.get(path).getMailbox();
                mailbox.schedule(new Envelope(message, mailbox, sender), time);
            }
        }
    }

    public final void sendMessageOnce(String path, Object message, long time, ActorRef sender) {
        synchronized (mailboxes) {
            if (!scopes.containsKey(path)) {
                if (sender != null) {
                    sender.send(new DeadLetter(message));
                }
            } else {
                Mailbox mailbox = scopes.get(path).getMailbox();
                mailbox.scheduleOnce(new Envelope(message, mailbox, sender), time);
            }
        }
    }


    /**
     * Processing of envelope
     *
     * @param envelope envelope
     */
    protected void processEnvelope(Envelope envelope) {
        ActorScope actor = null;
        synchronized (mailboxes) {
            actor = mailboxes.get(envelope.getMailbox());
        }
        if (actor == null) {
            //TODO: add logging
            return;
        }

        try {
            if (envelope.getMessage() == StartActor.INSTANCE) {
                try {
                    actor.createActor();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (envelope.getMessage() == PoisonPill.INSTANCE) {
                try {
                    actor.shutdownActor();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                CurrentActor.setCurrentActor(actor.getActor());
                actor.setSender(envelope.getSender());
                actor.getActor().onReceiveGlobal(envelope.getMessage());
            }
        } finally {
            dispatcher.getQueue().unlockMailbox(envelope.getMailbox());
            CurrentActor.setCurrentActor(null);
        }
    }
}
