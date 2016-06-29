package com.droidkit.actors;

/**
 * Object for manual actors creating
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public interface ActorCreator<T extends Actor> {
    /**
     * Create actor
     *
     * @return Actor
     */
    public T create();
}
