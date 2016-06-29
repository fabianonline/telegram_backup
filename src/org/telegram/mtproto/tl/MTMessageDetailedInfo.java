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
 * Date: 07.11.13
 * Time: 8:40
 */
public class MTMessageDetailedInfo extends TLObject {
    public static final int CLASS_ID = 0x276d3ec6;

    private long msgId;
    private long answerMsgId;
    private int bytes;
    private int state;

    public MTMessageDetailedInfo(long msgId, long answerMsgId, int bytes, int state) {
        this.msgId = msgId;
        this.answerMsgId = answerMsgId;
        this.bytes = bytes;
        this.state = state;
    }

    public MTMessageDetailedInfo() {

    }

    public long getMsgId() {
        return msgId;
    }

    public long getAnswerMsgId() {
        return answerMsgId;
    }

    public int getBytes() {
        return bytes;
    }

    public int getState() {
        return state;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeLong(msgId, stream);
        writeLong(answerMsgId, stream);
        writeInt(bytes, stream);
        writeInt(state, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        msgId = readLong(stream);
        answerMsgId = readLong(stream);
        bytes = readInt(stream);
        state = readInt(stream);
    }

    @Override
    public String toString() {
        return "msg_detailed_info#276d3ec6";
    }
}
