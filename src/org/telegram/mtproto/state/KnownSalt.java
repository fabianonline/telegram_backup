package org.telegram.mtproto.state;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 7:16
 */
public class KnownSalt {
    private int validSince;
    private int validUntil;
    private long salt;

    public KnownSalt(int validSince, int validUntil, long salt) {
        this.validSince = validSince;
        this.validUntil = validUntil;
        this.salt = salt;
    }

    public int getValidSince() {
        return validSince;
    }

    public int getValidUntil() {
        return validUntil;
    }

    public long getSalt() {
        return salt;
    }
}
