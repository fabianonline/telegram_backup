package de.fabianonline.telegram_backup;

import org.telegram.api.engine.storage.AbsApiState;
import org.telegram.api.TLConfig;
import org.telegram.mtproto.state.AbsMTProtoState;
import org.telegram.mtproto.state.ConnectionInfo;
import org.telegram.mtproto.state.KnownSalt;
import java.util.HashMap;

public class MyStorage implements AbsApiState {
	private int primaryDc = 2;
	private HashMap<Integer, Boolean> isAuthenticated = new HashMap<Integer, Boolean>();
	private HashMap<Integer, byte[]> authKeys = new HashMap<Integer, byte[]>();
	
	public int getPrimaryDc() { return primaryDc; }
	public void setPrimaryDc(int newDc) { this.primaryDc = newDc; }
	public boolean isAuthenticated(int dc) { 
		boolean temp = this.isAuthenticated.get(dc);
		if (temp) return true;
		return false;
	}
	public void setAuthenticated(int dc, boolean val) { this.isAuthenticated.put(dc, val); }
	
	public void updateSettings(TLConfig config) { System.out.println("Call to updateSettings"); }
	
	public byte[] getAuthKey(int dc) { return this.authKeys.get(dc); }
	
	public void putAuthKey(int dc, byte[] key) { this.authKeys.put(dc, key); }
	
	public ConnectionInfo[] getAvailableConnections(int dc) {
		switch(dc) {
			case 2: return new ConnectionInfo[]{new ConnectionInfo(1, 1, "149.154.167.50", 443)};
		}
		return new ConnectionInfo[0];
	}
	
	public AbsMTProtoState getMtProtoState(int dc) { System.out.println("Call to getMtProtoState"); return null; }
	
	public void resetAuth() { this.isAuthenticated.clear(); this.authKeys.clear(); }
	
	public void reset() { this.resetAuth(); this.primaryDc=2; }
}
