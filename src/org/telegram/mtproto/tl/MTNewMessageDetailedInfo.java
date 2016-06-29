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
 * Time: 8:37
 */
public class MTNewMessageDetailedInfo extends TLObject {

    public static final int CLASS_ID = 0x809db6df;

    private long answerMsgId;
    private int bytes;
    private int status;

    public MTNewMessageDetailedInfo(long answerMsgId, int bytes, int status) {
        this.answerMsgId = answerMsgId;
        this.bytes = bytes;
        this.status = status;
    }

    public MTNewMessageDetailedInfo() {

    }

    public long getAnswerMsgId() {
        return answerMsgId;
    }

    public int getBytes() {
        return bytes;
    }

    public int getStatus() {
        return status;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeLong(answerMsgId, stream);
        writeInt(bytes, stream);
        writeInt(status, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        answerMsgId = readLong(stream);
        bytes = readInt(stream);
        status = readInt(stream);
    }

    @Override
    public String toString() {
        return "msg_new_detailed_info#809db6df";
    }
}
