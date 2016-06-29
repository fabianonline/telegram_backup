package org.telegram.mtproto.tl;

import org.telegram.tl.TLContext;
import org.telegram.tl.TLObject;
import org.telegram.tl.TLVector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;

import static org.telegram.tl.StreamingUtils.*;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 20:53
 */
public class MTMessagesContainer extends TLObject {

    public static final int CLASS_ID = 0x73f1f8dc;

    private TreeSet<MTMessage> messages = new TreeSet<MTMessage>(new Comparator<MTMessage>() {
        @Override
        public int compare(MTMessage mtMessage, MTMessage mtMessage2) {
            return (int) Math.signum(mtMessage.getMessageId() - mtMessage2.getMessageId());
        }
    });

    public MTMessagesContainer(MTMessage[] messages) {
        Collections.addAll(this.messages, messages);
    }

    public MTMessagesContainer() {

    }

    public TreeSet<MTMessage> getMessages() {
        return messages;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeInt(messages.size(), stream);
        for (MTMessage message : messages) {
            message.serializeBody(stream);
        }
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        int size = readInt(stream);
        messages.clear();
        for (int i = 0; i < size; i++) {
            MTMessage message = new MTMessage();
            message.deserializeBody(stream, context);
            messages.add(message);
        }
    }

    @Override
    public String toString() {
        return "msg_container#73f1f8dc";
    }
}
