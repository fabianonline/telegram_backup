package org.telegram.mtproto.tl.pq;

import org.telegram.tl.TLContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.telegram.tl.StreamingUtils.*;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 6:31
 */
public class ServerDhFailure extends ServerDhParams {

    public static final int CLASS_ID = 0x79cb045d;

    protected byte[] nonce;
    protected byte[] serverNonce;
    protected byte[] newNonceHash;

    public ServerDhFailure(byte[] nonce, byte[] serverNonce, byte[] newNonceHash) {
        this.nonce = nonce;
        this.serverNonce = serverNonce;
        this.newNonceHash = newNonceHash;
    }

    public ServerDhFailure() {

    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
    }

    public byte[] getServerNonce() {
        return serverNonce;
    }

    public void setServerNonce(byte[] serverNonce) {
        this.serverNonce = serverNonce;
    }

    public byte[] getNewNonceHash() {
        return newNonceHash;
    }

    public void setNewNonceHash(byte[] newNonceHash) {
        this.newNonceHash = newNonceHash;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeByteArray(nonce, stream);
        writeByteArray(serverNonce, stream);
        writeByteArray(newNonceHash, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        nonce = readBytes(16, stream);
        serverNonce = readBytes(16, stream);
        newNonceHash = readBytes(16, stream);
    }

    @Override
    public String toString() {
        return "server_DH_params_fail#79cb045d";
    }
}
