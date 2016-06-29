package org.telegram.mtproto.tl.pq;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 7:20
 */
public class DhGenFailure extends DhGenResult {
    public static final int CLASS_ID = 0xa69dae02;

    public DhGenFailure(byte[] nonce, byte[] serverNonce, byte[] newNonceHash) {
        super(nonce, serverNonce, newNonceHash);
    }

    public DhGenFailure() {
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public String toString() {
        return "dh_gen_fail#a69dae02";
    }
}
