package com.droidkit.actors;

/**
 * <p>INTERNAL API!</p>
 * Keeps current actor for thread. Will be used for better implementations of patterns.
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class CurrentActor {
    private static ThreadLocal<Actor> currentActor = new ThreadLocal<Actor>();

    public static void setCurrentActor(Actor actor) {
        currentActor.set(actor);
    }

    public static Actor getCurrentActor() {
        return currentActor.get();
    }
}
