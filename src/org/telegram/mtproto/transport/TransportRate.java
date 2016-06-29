package org.telegram.mtproto.transport;

import org.telegram.mtproto.log.Logger;
import org.telegram.mtproto.state.ConnectionInfo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by ex3ndr on 26.11.13.
 */
public class TransportRate {

    private static final String TAG = "TransportRate";

    private HashMap<Integer, Transport> transports = new HashMap<Integer, Transport>();

    private Random rnd = new Random();

    public TransportRate(ConnectionInfo[] connectionInfos) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < connectionInfos.length; i++) {
            min = Math.min(connectionInfos[i].getPriority(), min);
            max = Math.max(connectionInfos[i].getPriority(), max);
        }
        for (int i = 0; i < connectionInfos.length; i++) {
            transports.put(connectionInfos[i].getId(),
                    new Transport(new ConnectionType(connectionInfos[i].getId(), connectionInfos[i].getAddress(), connectionInfos[i].getPort(), ConnectionType.TYPE_TCP),
                            connectionInfos[i].getPriority() - min + 1));
        }
        normalize();
    }

    public synchronized ConnectionType tryConnection() {
        float value = rnd.nextFloat();
        Transport[] currentTransports = transports.values().toArray(new Transport[0]);
        Arrays.sort(currentTransports, new Comparator<Transport>() {
            @Override
            public int compare(Transport transport, Transport transport2) {
                return -Float.compare(transport.getRate(), transport2.getRate());
            }
        });
        ConnectionType type = currentTransports[0].getConnectionType();
        Logger.d(TAG, "tryConnection #" + type.getId());
        return type;
    }

    public synchronized void onConnectionFailure(int id) {
        Logger.d(TAG, "onConnectionFailure #" + id);
        transports.get(id).rate *= 0.5;
        normalize();
    }

    public synchronized void onConnectionSuccess(int id) {
        Logger.d(TAG, "onConnectionSuccess #" + id);
        transports.get(id).rate *= 1.5;
        normalize();
    }

    private synchronized void normalize() {
        float sum = 0;
        for (Integer id : transports.keySet()) {
            sum += transports.get(id).rate;
        }
        for (Integer id : transports.keySet()) {
            Transport transport = transports.get(id);
            transport.rate /= sum;
            Logger.d(TAG, "Transport: #" + transport.connectionType.getId() + " " + transport.connectionType.getHost() + ":" + transport.getConnectionType().getPort() + " #" + transport.getRate());
        }
    }

    private class Transport {
        private ConnectionType connectionType;
        private float rate;

        private Transport(ConnectionType connectionType, float rate) {
            this.connectionType = connectionType;
            this.rate = rate;
        }

        public ConnectionType getConnectionType() {
            return connectionType;
        }

        public void setConnectionType(ConnectionType connectionType) {
            this.connectionType = connectionType;
        }

        public float getRate() {
            return rate;
        }

        public void setRate(float rate) {
            this.rate = rate;
        }
    }
}
