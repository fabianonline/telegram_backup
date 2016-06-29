package org.telegram.mtproto.tl;

import org.telegram.tl.TLContext;
import org.telegram.tl.TLObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.telegram.tl.StreamingUtils.*;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 8:00
 */
public class MTFutureSalt extends TLObject {

    public static final int CLASS_ID = 0x0949d9dc;

    private int validSince;
    private int validUntil;
    private long salt;

    public MTFutureSalt(int validSince, int validUntil, long salt) {
        this.validSince = validSince;
        this.validUntil = validUntil;
        this.salt = salt;
    }

    public MTFutureSalt() {

    }

    @Override
    public int getClassId() {
        return CLASS_ID;
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

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeInt(validSince, stream);
        writeInt(validUntil, stream);
        writeLong(salt, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        validSince = readInt(stream);
        validUntil = readInt(stream);
        salt = readLong(stream);
    }

    @Override
    public String toString() {
        return "future_salt#0949d9dc";
    }
}
