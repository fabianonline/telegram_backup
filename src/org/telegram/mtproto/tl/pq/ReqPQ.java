package org.telegram.mtproto.tl.pq;

import static org.telegram.tl.StreamingUtils.*;

import org.telegram.tl.DeserializeException;
import org.telegram.tl.TLContext;
import org.telegram.tl.TLMethod;
import org.telegram.tl.TLObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 4:17
 */
public class ReqPQ extends TLMethod<ResPQ> {

    public static final int CLASS_ID = 0x60469778;

    protected byte[] nonce;

    public ReqPQ(byte[] nonce) {
        if (nonce == null || nonce.length != 16) {
            throw new IllegalArgumentException("nonce might be not null and 16 bytes length");
        }
        this.nonce = nonce;
    }

    public ReqPQ() {

    }

    @Override
    public ResPQ deserializeResponse(InputStream stream, TLContext context) throws IOException {
        TLObject response = context.deserializeMessage(stream);
        if (response == null) {
            throw new DeserializeException("Unable to deserialize response");
        }
        if (!(response instanceof ResPQ)) {
            throw new DeserializeException("Response has incorrect type");
        }

        return (ResPQ) response;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] nonce) {
        if (nonce == null || nonce.length != 16) {
            throw new IllegalArgumentException("nonce might be not null and 16 bytes length");
        }
        this.nonce = nonce;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeByteArray(nonce, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        nonce = readBytes(16, stream);
    }

    @Override
    public String toString() {
        return "req_pq#60469778";
    }
}
