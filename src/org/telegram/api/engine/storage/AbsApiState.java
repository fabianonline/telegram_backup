package org.telegram.api.engine.storage;

import org.telegram.api.TLConfig;
import org.telegram.mtproto.state.AbsMTProtoState;
import org.telegram.mtproto.state.ConnectionInfo;
import org.telegram.mtproto.state.KnownSalt;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 10:19
 */
public interface AbsApiState {

    int getPrimaryDc();

    public void setPrimaryDc(int dc);

    boolean isAuthenticated(int dcId);

    void setAuthenticated(int dcId, boolean auth);

    void updateSettings(TLConfig config);


    byte[] getAuthKey(int dcId);

    void putAuthKey(int dcId, byte[] key);

    ConnectionInfo[] getAvailableConnections(int dcId);

    AbsMTProtoState getMtProtoState(int dcId);

    void resetAuth();

    void reset();
}
