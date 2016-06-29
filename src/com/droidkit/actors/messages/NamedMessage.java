package com.droidkit.actors.messages;

/**
 * Simple named message
 */
public class NamedMessage {
    private String name;
    private Object message;

    public NamedMessage(String name, Object message) {
        this.name = name;
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public Object getMessage() {
        return message;
    }
}
