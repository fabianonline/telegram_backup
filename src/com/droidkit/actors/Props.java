package com.droidkit.actors;

/**
 * <p>Props is a configuration class to specify options for the creation of actors, think of it as an immutable and
 * thus freely shareable recipe for creating an actor including associated dispatcher information.</p>
 * For more information you may read about <a href="http://doc.akka.io/docs/akka/2.3.5/java/untyped-actors.html">Akka Props</a>.
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public final class Props<T extends Actor> {
    private static final int TYPE_DEFAULT = 1;
    private static final int TYPE_CREATOR = 2;

    private final Class<T> aClass;
    private final Object[] args;
    private final int type;
    private final ActorCreator<T> creator;

    private final String dispatcher;

    private Props(Class<T> aClass, Object[] args, int type, String dispatcher, ActorCreator<T> creator) {
        this.aClass = aClass;
        this.args = args;
        this.type = type;
        this.creator = creator;
        this.dispatcher = dispatcher;
    }

    /**
     * Creating actor from Props
     *
     * @return Actor
     * @throws Exception
     */
    public T create() throws Exception {
        if (type == TYPE_DEFAULT) {
            if (args == null || args.length == 0) {
                return aClass.newInstance();
            }
        } else if (type == TYPE_CREATOR) {
            return creator.create();
        }

        throw new RuntimeException("Unsupported create method");
    }

    /**
     * Getting dispatcher id if available
     *
     * @return
     */
    public String getDispatcher() {
        return dispatcher;
    }

    /**
     * Changing dispatcher
     *
     * @param dispatcher dispatcher id
     * @return this
     */
    public Props<T> changeDispatcher(String dispatcher) {
        return new Props<T>(aClass, args, type, dispatcher, creator);
    }

    /**
     * Create props from class
     *
     * @param tClass Actor class
     * @param <T>    Actor class
     * @return Props object
     */
    public static <T extends Actor> Props<T> create(Class<T> tClass) {
        return new Props(tClass, null, TYPE_DEFAULT, null, null);
    }

    /**
     * Create props from Actor creator
     *
     * @param clazz   Actor class
     * @param creator Actor creator class
     * @param <T>     Actor class
     * @return Props object
     */
    public static <T extends Actor> Props<T> create(Class<T> clazz, ActorCreator<T> creator) {
        return new Props<T>(clazz, null, TYPE_CREATOR, null, creator);
    }
}
