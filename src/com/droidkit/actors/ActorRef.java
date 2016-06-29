package com.droidkit.actors;

import com.droidkit.actors.mailbox.AbsActorDispatcher;

import java.util.UUID;

/**
 * Reference to Actor that allows to send messages to real Actor
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class ActorRef {
    private ActorSystem system;
    private AbsActorDispatcher dispatcher;
    private UUID uuid;
    private String path;

    public UUID getUuid() {
        return uuid;
    }

    public String getPath() {
        return path;
    }

    /**
     * <p>INTERNAL API</p>
     * Creating actor reference
     *
     * @param system     actor system
     * @param dispatcher dispatcher of actor
     * @param path       path of actor
     * @param uuid       uuid of actor
     */
    public ActorRef(ActorSystem system, AbsActorDispatcher dispatcher, UUID uuid, String path) {
        this.system = system;
        this.dispatcher = dispatcher;
        this.uuid = uuid;
        this.path = path;
    }

    /**
     * Send message with empty sender
     *
     * @param message message
     */
    public void send(Object message) {
        send(message, null);
    }

    /**
     * Send message with specified sender
     *
     * @param message message
     * @param sender  sender
     */
    public void send(Object message, ActorRef sender) {
        send(message, 0, sender);
    }

    /**
     * Send message with empty sender and delay
     *
     * @param message message
     * @param delay   delay
     */
    public void send(Object message, long delay) {
        send(message, delay, null);
    }

    /**
     * Send message
     *
     * @param message message
     * @param delay   delay
     * @param sender  sender
     */
    public void send(Object message, long delay, ActorRef sender) {
        dispatcher.sendMessage(path, message, ActorTime.currentTime() + delay, sender);
    }

    /**
     * Send message once
     *
     * @param message message
     */
    public void sendOnce(Object message) {
        send(message, null);
    }

    /**
     * Send message once
     *
     * @param message message
     * @param sender  sender
     */
    public void sendOnce(Object message, ActorRef sender) {
        sendOnce(message, 0, sender);
    }

    /**
     * Send message once
     *
     * @param message message
     * @param delay   delay
     */
    public void sendOnce(Object message, long delay) {
        sendOnce(message, delay, null);
    }

    /**
     * Send message once
     *
     * @param message message
     * @param delay   delay
     * @param sender  sender
     */
    public void sendOnce(Object message, long delay, ActorRef sender) {
        dispatcher.sendMessageOnce(path, message, ActorTime.currentTime() + delay, sender);
    }
}
