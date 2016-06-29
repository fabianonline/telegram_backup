package com.droidkit.actors.mailbox;

import com.droidkit.actors.ActorRef;

/**
 * Actor system envelope
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class Envelope {
    private final Object message;
    private final ActorRef sender;
    private final Mailbox mailbox;

    /**
     * Creating of envelope
     *
     * @param message message
     * @param mailbox mailbox
     * @param sender  sender reference
     */
    public Envelope(Object message, Mailbox mailbox, ActorRef sender) {
        this.message = message;
        this.sender = sender;
        this.mailbox = mailbox;
    }

    /**
     * Message in envelope
     *
     * @return message
     */
    public Object getMessage() {
        return message;
    }

    /**
     * Mailbox for envelope
     *
     * @return mailbox
     */
    public Mailbox getMailbox() {
        return mailbox;
    }

    /**
     * Sender of message
     *
     * @return sender reference
     */
    public ActorRef getSender() {
        return sender;
    }
}
