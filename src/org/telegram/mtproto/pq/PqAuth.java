package org.telegram.mtproto.pq;

import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 8:14
 */
public class PqAuth {
    private byte[] authKey;
    private long serverSalt;
    private Socket socket;

    public PqAuth(byte[] authKey, long serverSalt, Socket socket) {
        this.authKey = authKey;
        this.serverSalt = serverSalt;
        this.socket = socket;
    }

    public byte[] getAuthKey() {
        return authKey;
    }

    public long getServerSalt() {
        return serverSalt;
    }

    public Socket getSocket() {
        return socket;
    }
}
