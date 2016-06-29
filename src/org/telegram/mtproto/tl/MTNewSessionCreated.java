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
 * Time: 8:35
 */
public class MTNewSessionCreated extends TLObject {

    public static final int CLASS_ID = 0x9ec20908;

    private long firstMsgId;
    private byte[] uniqId;
    private byte[] serverSalt;

    public MTNewSessionCreated(long firstMsgId, byte[] uniqId, byte[] serverSalt) {
        this.firstMsgId = firstMsgId;
        this.uniqId = uniqId;
        this.serverSalt = serverSalt;
    }

    public MTNewSessionCreated() {

    }

    public long getFirstMsgId() {
        return firstMsgId;
    }

    public byte[] getUniqId() {
        return uniqId;
    }

    public byte[] getServerSalt() {
        return serverSalt;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeLong(firstMsgId, stream);
        writeByteArray(uniqId, stream);
        writeByteArray(serverSalt, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        firstMsgId = readLong(stream);
        uniqId = readBytes(8, stream);
        serverSalt = readBytes(8, stream);
    }

    @Override
    public String toString() {
        return "new_session_created#9ec20908";
    }
}
