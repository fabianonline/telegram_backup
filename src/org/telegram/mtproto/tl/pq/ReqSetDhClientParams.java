package org.telegram.mtproto.tl.pq;

import org.telegram.tl.DeserializeException;
import org.telegram.tl.TLContext;
import org.telegram.tl.TLMethod;
import org.telegram.tl.TLObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.telegram.tl.StreamingUtils.*;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 7:31
 */
public class ReqSetDhClientParams extends TLMethod<DhGenResult> {

    public static final int CLASS_ID = 0xf5045f1f;

    @Override
    public DhGenResult deserializeResponse(InputStream stream, TLContext context) throws IOException {
        TLObject response = context.deserializeMessage(stream);
        if (response == null) {
            throw new DeserializeException("Unable to deserialize response");
        }
        if (!(response instanceof DhGenResult)) {
            throw new DeserializeException("Response has incorrect type");
        }
        return (DhGenResult) response;
    }

    protected byte[] nonce;
    protected byte[] serverNonce;
    protected byte[] encrypted;

    public ReqSetDhClientParams(byte[] nonce, byte[] serverNonce, byte[] encrypted) {
        this.nonce = nonce;
        this.serverNonce = serverNonce;
        this.encrypted = encrypted;
    }

    public ReqSetDhClientParams() {

    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getServerNonce() {
        return serverNonce;
    }

    public byte[] getEncrypted() {
        return encrypted;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeByteArray(nonce, stream);
        writeByteArray(serverNonce, stream);
        writeTLBytes(encrypted, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        nonce = readBytes(16, stream);
        serverNonce = readBytes(16, stream);
        encrypted = readTLBytes(stream);
    }

    @Override
    public String toString() {
        return "set_client_DH_params#f5045f1f";
    }
}