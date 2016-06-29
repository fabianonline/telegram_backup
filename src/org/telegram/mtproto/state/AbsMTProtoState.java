package org.telegram.mtproto.state;

import org.telegram.mtproto.time.TimeOverlord;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 7:15
 */
public abstract class AbsMTProtoState {

    public abstract byte[] getAuthKey();

    public abstract ConnectionInfo[] getAvailableConnections();

    public abstract KnownSalt[] readKnownSalts();

    protected abstract void writeKnownSalts(KnownSalt[] salts);

    public void mergeKnownSalts(int currentTime, KnownSalt[] salts) {
        KnownSalt[] knownSalts = readKnownSalts();
        HashMap<Long, KnownSalt> ids = new HashMap<Long, KnownSalt>();
        for (KnownSalt s : knownSalts) {
            if (s.getValidUntil() < currentTime) {
                continue;
            }
            ids.put(s.getSalt(), s);
        }
        for (KnownSalt s : salts) {
            if (s.getValidUntil() < currentTime) {
                continue;
            }
            ids.put(s.getSalt(), s);
        }
        writeKnownSalts(ids.values().toArray(new KnownSalt[0]));
    }

    public void addCurrentSalt(long salt) {
        int time = (int) (TimeOverlord.getInstance().getServerTime() / 1000);
        mergeKnownSalts(time, new KnownSalt[]{new KnownSalt(time, time + 30 * 60, salt)});
    }

    public void badServerSalt(long salt) {
        int time = (int) (TimeOverlord.getInstance().getServerTime() / 1000);
        writeKnownSalts(new KnownSalt[]{new KnownSalt(time, time + 30 * 60, salt)});
    }

    public void initialServerSalt(long salt) {
        int time = (int) (TimeOverlord.getInstance().getServerTime() / 1000);
        writeKnownSalts(new KnownSalt[]{new KnownSalt(time, time + 30 * 60, salt)});
    }

    public long findActualSalt(int time) {
        KnownSalt[] knownSalts = readKnownSalts();
        for (KnownSalt salt : knownSalts) {
            if (salt.getValidSince() <= time && time <= salt.getValidUntil()) {
                return salt.getSalt();
            }
        }

        return 0;
    }

    public int maximumCachedSalts(int time) {
        int count = 0;
        for (KnownSalt salt : readKnownSalts()) {
            if (salt.getValidSince() > time) {
                count++;
            }
        }
        return count;
    }

    public int maximumCachedSaltsTime() {
        int max = 0;
        for (KnownSalt salt : readKnownSalts()) {
            max = Math.max(max, salt.getValidUntil());
        }
        return max;
    }
}
