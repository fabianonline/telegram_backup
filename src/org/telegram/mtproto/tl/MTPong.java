package org.telegram.mtproto.tl;

import org.telegram.tl.TLContext;
import org.telegram.tl.TLObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.telegram.tl.StreamingUtils.*;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 8:24
 */
public class MTPong extends TLObject {

    public static final int CLASS_ID = 0x347773c5;

    private long messageId;
    private long pingId;

    public MTPong(long messageId, long pingId) {
        this.messageId = messageId;
        this.pingId = pingId;
    }

    public MTPong() {
    }

    public long getMessageId() {
        return messageId;
    }

    public long getPingId() {
        return pingId;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeLong(messageId, stream);
        writeLong(pingId, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        messageId = readLong(stream);
        pingId = readLong(stream);
    }

    @Override
    public String toString() {
        return "pong#347773c5";
    }
}
