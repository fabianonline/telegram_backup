package org.telegram.mtproto.state;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 7:21
 */
public class MemoryProtoState extends AbsMTProtoState {

    private KnownSalt[] salts = new KnownSalt[0];

    private String address;
    private int port;
    private byte[] authKey;

    public MemoryProtoState(byte[] authKey, String address, int port) {
        this.authKey = authKey;
        this.port = port;
        this.address = address;
    }

    @Override
    public byte[] getAuthKey() {
        return authKey;
    }

    @Override
    public ConnectionInfo[] getAvailableConnections() {
        return new ConnectionInfo[]{new ConnectionInfo(0, 0, address, port)};
    }

    @Override
    public KnownSalt[] readKnownSalts() {
        return salts;
    }

    @Override
    protected void writeKnownSalts(KnownSalt[] salts) {
        this.salts = salts;
    }
}
