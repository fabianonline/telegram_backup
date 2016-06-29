package org.telegram.mtproto.tl.pq;

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
 * Time: 7:32
 */
public class ClientDhInner extends TLObject {
    protected byte[] nonce;
    protected byte[] serverNonce;
    protected long retryId;
    protected byte[] gb;

    public static final int CLASS_ID = 0x6643b654;

    public ClientDhInner(byte[] nonce, byte[] serverNonce, long retryId, byte[] gb) {
        this.nonce = nonce;
        this.serverNonce = serverNonce;
        this.retryId = retryId;
        this.gb = gb;
    }

    public ClientDhInner() {

    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getServerNonce() {
        return serverNonce;
    }

    public long getRetryId() {
        return retryId;
    }

    public byte[] getGb() {
        return gb;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeByteArray(nonce, stream);
        writeByteArray(serverNonce, stream);
        writeLong(retryId, stream);
        writeTLBytes(gb, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        nonce = readBytes(16, stream);
        serverNonce = readBytes(16, stream);
        retryId = readLong(stream);
        gb = readTLBytes(stream);
    }
}
