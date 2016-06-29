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
 * Time: 6:20
 */
public class PQInner extends TLObject {
    public static final int CLASS_ID = 0x83c95aec;

    protected byte[] pq;
    protected byte[] p;
    protected byte[] q;
    protected byte[] nonce;
    protected byte[] serverNonce;
    protected byte[] newNonce;

    public PQInner(byte[] pq, byte[] p, byte[] q, byte[] nonce, byte[] serverNonce, byte[] newNonce) {
        this.pq = pq;
        this.p = p;
        this.q = q;
        this.nonce = nonce;
        this.serverNonce = serverNonce;
        this.newNonce = newNonce;
    }

    public PQInner() {

    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    public byte[] getPq() {
        return pq;
    }

    public byte[] getP() {
        return p;
    }

    public byte[] getQ() {
        return q;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getServerNonce() {
        return serverNonce;
    }

    public byte[] getNewNonce() {
        return newNonce;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeTLBytes(pq, stream);
        writeTLBytes(p, stream);
        writeTLBytes(q, stream);
        writeByteArray(nonce, stream);
        writeByteArray(serverNonce, stream);
        writeByteArray(newNonce, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        pq = readTLBytes(stream);
        p = readTLBytes(stream);
        q = readTLBytes(stream);
        nonce = readBytes(16, stream);
        serverNonce = readBytes(16, stream);
        newNonce = readBytes(32, stream);
    }
}
