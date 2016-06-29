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
 * Time: 6:56
 */
public class ServerDhInner extends TLObject {
    public static final int CLASS_ID = 0xb5890dba;

    protected byte[] nonce;
    protected byte[] serverNonce;
    protected int g;
    protected byte[] dhPrime;
    protected byte[] g_a;
    protected int serverTime;

    public ServerDhInner(byte[] nonce, byte[] serverNonce, int g, byte[] dhPrime, byte[] g_a, int serverTime) {
        this.nonce = nonce;
        this.serverNonce = serverNonce;
        this.g = g;
        this.dhPrime = dhPrime;
        this.g_a = g_a;
        this.serverTime = serverTime;
    }

    public ServerDhInner() {

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

    public int getG() {
        return g;
    }

    public byte[] getDhPrime() {
        return dhPrime;
    }

    public byte[] getG_a() {
        return g_a;
    }

    public int getServerTime() {
        return serverTime;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeByteArray(nonce, stream);
        writeByteArray(serverNonce, stream);
        writeInt(g, stream);
        writeTLBytes(dhPrime, stream);
        writeTLBytes(g_a, stream);
        writeInt(serverTime, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        nonce = readBytes(16, stream);
        serverNonce = readBytes(16, stream);
        g = readInt(stream);
        dhPrime = readTLBytes(stream);
        g_a = readTLBytes(stream);
        serverTime = readInt(stream);
    }
}
