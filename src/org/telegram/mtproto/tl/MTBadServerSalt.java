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
 * Time: 8:45
 */
public class MTBadServerSalt extends MTBadMessage {

    public static final int CLASS_ID = 0xedab447b;

    private long newSalt;

    public MTBadServerSalt(long messageId, int seqNo, int errorNo, long newSalt) {
        this.badMsgId = messageId;
        this.badMsqSeqno = seqNo;
        this.errorCode = errorNo;
        this.newSalt = newSalt;
    }

    public MTBadServerSalt() {

    }

    public long getNewSalt() {
        return newSalt;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeLong(badMsgId, stream);
        writeInt(badMsqSeqno, stream);
        writeInt(errorCode, stream);
        writeLong(newSalt, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        badMsgId = readLong(stream);
        badMsqSeqno = readInt(stream);
        errorCode = readInt(stream);
        newSalt = readLong(stream);
    }

    @Override
    public String toString() {
        return "bad_server_salt#edab447b";
    }
}
