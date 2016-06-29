package com.droidkit.actors;

import com.droidkit.actors.messages.NamedMessage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * ReflectedActor is Actor that uses java reflection for processing of messages
 * For each message developer must create method named "onReceive" with one argument
 * with type of message
 * For special message {@link com.droidkit.actors.messages.NamedMessage} you can create method
 * named like {@code onDownloadReceive}. First letter in {@code Download} will be lowed and ReflectedActor
 * will use this as {@code download} for name of message.
 *
 * @author Stepan Ex3NDR Korshakov (me@ex3ndr.com)
 */
public class ReflectedActor extends Actor {

    private ArrayList<Event> events = new ArrayList<Event>();
    private ArrayList<NamedEvent> namedEvents = new ArrayList<NamedEvent>();

    @Override
    public final void preStart() {
        Method[] methods = getClass().getDeclaredMethods();
        for (Method m : methods) {
            if (m.getName().startsWith("onReceive") && m.getParameterTypes().length == 1) {
                if (m.getName().equals("onReceive") && m.getParameterTypes()[0] == Object.class) {
                    continue;
                }
                events.add(new Event(m.getParameterTypes()[0], m));
                continue;
            }
            if (m.getName().startsWith("on") && m.getName().endsWith("Receive")) {
                String methodName = m.getName();
                String name = methodName.substring("on".length(), methodName.length() - "Receive".length());
                if (name.length() > 0) {
                    name = name.substring(0, 1).toLowerCase() + name.substring(1);
                    namedEvents.add(new NamedEvent(name, m.getParameterTypes()[0], m));
                    continue;
                }

            }
        }
        preStartImpl();
    }

    /**
     * Replacement for preStart
     */
    public void preStartImpl() {

    }

    @Override
    public void onReceive(Object message) {
        if (message instanceof NamedMessage) {
            NamedMessage named = (NamedMessage) message;
            for (NamedEvent event : namedEvents) {
                if (event.name.equals(named.getName())) {
                    if (event.check(named.getMessage())) {
                        try {
                            event.method.invoke(this, named.getMessage());
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        for (Event event : events) {
            if (event.check(message)) {
                try {
                    event.method.invoke(this, message);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
                return;
            }
        }
    }

    class NamedEvent {
        private String name;
        private Class arg;
        private Method method;

        NamedEvent(String name, Class arg, Method method) {
            this.name = name;
            this.arg = arg;
            this.method = method;
        }

        public String getName() {
            return name;
        }

        public Class getArg() {
            return arg;
        }

        public Method getMethod() {
            return method;
        }

        public boolean check(Object obj) {
            if (arg.isAssignableFrom(obj.getClass())) {
                return true;
            }
            return false;
        }
    }

    class Event {
        private Class arg;
        private Method method;

        Event(Class arg, Method method) {
            this.arg = arg;
            this.method = method;
        }

        public boolean check(Object obj) {
            if (arg.isAssignableFrom(obj.getClass())) {
                return true;
            }
            return false;
        }
    }
}
