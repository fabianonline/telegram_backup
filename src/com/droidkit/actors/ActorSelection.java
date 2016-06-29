package com.droidkit.actors;

/**
 * Actor selection: group and path of actor
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class ActorSelection {
    private final Props props;
    private final String path;

    public ActorSelection(Props props, String path) {
        this.props = props;
        this.path = path;
    }

    public Props getProps() {
        return props;
    }

    public String getPath() {
        return path;
    }
}
