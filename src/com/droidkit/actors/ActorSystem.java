package com.droidkit.actors;

import com.droidkit.actors.mailbox.AbsActorDispatcher;
import com.droidkit.actors.mailbox.ActorDispatcher;

import java.util.HashMap;

/**
 * Entry point for Actor Model, creates all actors and dispatchers
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class ActorSystem {

    private static final ActorSystem mainSystem = new ActorSystem();

    /**
     * Main actor system
     *
     * @return ActorSystem
     */
    public static ActorSystem system() {
        return mainSystem;
    }

    private static final String DEFAULT_DISPATCHER = "default";

    private final HashMap<String, AbsActorDispatcher> dispatchers = new HashMap<String, AbsActorDispatcher>();
    private final HashMap<String, ActorScope> actors = new HashMap<String, ActorScope>();

    /**
     * Creating new actor system
     */
    public ActorSystem() {
        addDispatcher(DEFAULT_DISPATCHER);
    }

    /**
     * Adding dispatcher with threads count = {@code Runtime.getRuntime().availableProcessors()}
     *
     * @param dispatcherId dispatcher id
     */
    public void addDispatcher(String dispatcherId) {
        addDispatcher(dispatcherId, new ActorDispatcher(this, Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Registering custom dispatcher
     *
     * @param dispatcherId dispatcher id
     * @param dispatcher   dispatcher object
     */
    public void addDispatcher(String dispatcherId, AbsActorDispatcher dispatcher) {
        synchronized (dispatchers) {
            if (dispatchers.containsKey(dispatcherId)) {
                return;
            }
            dispatchers.put(dispatcherId, dispatcher);
        }
    }

    public <T extends Actor> ActorRef actorOf(ActorSelection selection) {
        return actorOf(selection.getProps(), selection.getPath());
    }

    /**
     * Creating or getting existing actor from actor class
     *
     * @param actor Actor Class
     * @param path  Actor Path
     * @param <T>   Actor Class
     * @return ActorRef
     */
    public <T extends Actor> ActorRef actorOf(Class<T> actor, String path) {
        return actorOf(Props.create(actor), path);
    }

    /**
     * Creating or getting existing actor from actor props
     *
     * @param props Actor Props
     * @param path  Actor Path
     * @return ActorRef
     */
    public ActorRef actorOf(Props props, String path) {
        // TODO: Remove lock
        synchronized (actors) {
            // Searching for already created actor
            ActorScope scope = actors.get(path);

            // If already created - return ActorRef
            if (scope != null) {
                return scope.getActorRef();
            }

            // Finding dispatcher for actor
            String dispatcherId = props.getDispatcher() == null ? DEFAULT_DISPATCHER : props.getDispatcher();

            AbsActorDispatcher mailboxesDispatcher;
            synchronized (dispatchers) {
                if (!dispatchers.containsKey(dispatcherId)) {
                    throw new RuntimeException("Unknown dispatcherId '" + dispatcherId + "'");
                }
                mailboxesDispatcher = dispatchers.get(dispatcherId);
            }

            // Creating actor scope
            scope = mailboxesDispatcher.createScope(path, props);

            // Saving actor in collection
            actors.put(path, scope);

            return scope.getActorRef();
        }
    }

    /**
     * WARRING! Call only during processing message in actor!
     *
     * @param scope Actor Scope
     */
    void removeActor(ActorScope scope) {
        synchronized (actors) {
            if (actors.get(scope.getPath()) == scope) {
                actors.remove(scope.getPath());
            }
        }
    }
}