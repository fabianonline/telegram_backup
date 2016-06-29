package org.telegram.mtproto.transport;

/**
 * Created by ex3ndr on 26.11.13.
 */
public class ConnectionType {
    public static final int TYPE_TCP = 0;

    private int id;
    private String host;
    private int port;
    private int connectionType;

    public ConnectionType(int id, String host, int port, int connectionType) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.connectionType = connectionType;
    }

    public int getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getConnectionType() {
        return connectionType;
    }
}
