package org.telegram.mtproto.tl;

import org.telegram.tl.TLContext;
import org.telegram.tl.TLObject;
import org.telegram.tl.TLVector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.telegram.tl.StreamingUtils.*;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 7:58
 */
public class MTFutureSalts extends TLObject {
    public static final int CLASS_ID = 0xae500895;

    private long requestId;
    private int now;
    private TLVector<MTFutureSalt> salts = new TLVector<MTFutureSalt>();

    public MTFutureSalts(long requestId, int now, TLVector<MTFutureSalt> salts) {
        this.requestId = requestId;
        this.now = now;
        this.salts = salts;
    }

    public MTFutureSalts() {

    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    public long getRequestId() {
        return requestId;
    }

    public int getNow() {
        return now;
    }

    public TLVector<MTFutureSalt> getSalts() {
        return salts;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeLong(requestId, stream);
        writeInt(now, stream);
        writeInt(salts.size(), stream);
        for (MTFutureSalt salt : salts) {
            salt.serializeBody(stream);
        }
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        requestId = readLong(stream);
        now = readInt(stream);
        int count = readInt(stream);
        salts.clear();
        for (int i = 0; i < count; i++) {
            MTFutureSalt salt = new MTFutureSalt();
            salt.deserializeBody(stream, context);
            salts.add(salt);
        }
    }

    @Override
    public String toString() {
        return "future_salts#ae500895";
    }
}
