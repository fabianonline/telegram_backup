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
 * Time: 6:26
 */
public class ReqDhParams extends TLMethod<ServerDhParams> {

    public static final int CLASS_ID = 0xd712e4be;

    @Override
    public ServerDhParams deserializeResponse(InputStream stream, TLContext context) throws IOException {
        TLObject response = context.deserializeMessage(stream);

        if (response == null) {
            throw new DeserializeException("Unable to deserialize response");
        }
        if (!(response instanceof ServerDhParams)) {
            throw new DeserializeException("Response has incorrect type");
        }

        return (ServerDhParams) response;
    }

    protected byte[] nonce;
    protected byte[] serverNonce;
    protected byte[] p;
    protected byte[] q;
    protected long fingerPrint;
    protected byte[] encryptedData;

    public ReqDhParams(byte[] nonce, byte[] serverNonce, byte[] p, byte[] q, long fingerPrint, byte[] encryptedData) {
        this.nonce = nonce;
        this.serverNonce = serverNonce;
        this.p = p;
        this.q = q;
        this.fingerPrint = fingerPrint;
        this.encryptedData = encryptedData;
    }

    public ReqDhParams() {

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

    public byte[] getP() {
        return p;
    }

    public byte[] getQ() {
        return q;
    }

    public long getFingerPrint() {
        return fingerPrint;
    }

    public byte[] getEncryptedData() {
        return encryptedData;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeByteArray(nonce, stream);
        writeByteArray(serverNonce, stream);
        writeTLBytes(p, stream);
        writeTLBytes(q, stream);
        writeLong(fingerPrint, stream);
        writeTLBytes(encryptedData, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        nonce = readBytes(16, stream);
        serverNonce = readBytes(16, stream);
        p = readTLBytes(stream);
        q = readTLBytes(stream);
        fingerPrint = readLong(stream);
        encryptedData = readTLBytes(stream);
    }

    @Override
    public String toString() {
        return "req_DH_params#d712e4be";
    }
}
