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
 * Time: 6:29
 */
public class ServerDhOk extends ServerDhParams {

    public static final int CLASS_ID = 0xd0e8075c;

    protected byte[] nonce;
    protected byte[] serverNonce;
    protected byte[] encryptedAnswer;

    public ServerDhOk(byte[] nonce, byte[] serverNonce, byte[] encryptedAnswer) {
        this.nonce = nonce;
        this.serverNonce = serverNonce;
        this.encryptedAnswer = encryptedAnswer;
    }

    public ServerDhOk() {

    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getServerNonce() {
        return serverNonce;
    }

    public byte[] getEncryptedAnswer() {
        return encryptedAnswer;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeByteArray(nonce, stream);
        writeByteArray(serverNonce, stream);
        writeTLBytes(encryptedAnswer, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        nonce = readBytes(16, stream);
        serverNonce = readBytes(16, stream);
        encryptedAnswer = readTLBytes(stream);
    }

    @Override
    public String toString() {
        return "server_DH_params_ok#d0e8075c";
    }
}
