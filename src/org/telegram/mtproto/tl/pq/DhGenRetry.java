package org.telegram.mtproto.tl.pq;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 7:20
 */
public class DhGenRetry extends DhGenResult {
    public static final int CLASS_ID = 0x46dc1fb9;

    public DhGenRetry(byte[] nonce, byte[] serverNonce, byte[] newNonceHash) {
        super(nonce, serverNonce, newNonceHash);
    }

    public DhGenRetry() {
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public String toString() {
        return "dh_gen_retry#46dc1fb9";
    }
}
