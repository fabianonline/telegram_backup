package com.droidkit.actors.android;

import com.droidkit.actors.ActorSystem;
import com.droidkit.actors.dispatch.Dispatch;
import com.droidkit.actors.mailbox.AbsActorDispatcher;
import com.droidkit.actors.mailbox.Envelope;
import com.droidkit.actors.mailbox.MailboxesQueue;

/**
 * Actor Dispatcher for dispatching messages on UI Thread
 */
public class UiActorDispatcher extends AbsActorDispatcher {

    public UiActorDispatcher(ActorSystem actorSystem) {
        super(actorSystem);

        initDispatcher(new UiDispatcher<Envelope, MailboxesQueue>(new MailboxesQueue(), new Dispatch<Envelope>() {
            @Override
            public void dispatchMessage(Envelope message) {
                processEnvelope(message);
            }
        }));
    }
}
