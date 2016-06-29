package org.telegram.mtproto.tl;

import org.telegram.tl.TLContext;
import org.telegram.tl.TLObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.telegram.tl.StreamingUtils.*;

/**
 * Created by ex3ndr on 16.12.13.
 */
public class MTInvokeAfter extends TLObject {
    public static final int CLASS_ID = 0xcb9f372d;

    private long dependMsgId;

    private byte[] request;

    public MTInvokeAfter(long dependMsgId, byte[] request) {
        this.dependMsgId = dependMsgId;
        this.request = request;
    }

    public long getDependMsgId() {
        return dependMsgId;
    }

    public byte[] getRequest() {
        return request;
    }

    @Override
    public int getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serializeBody(OutputStream stream) throws IOException {
        writeLong(dependMsgId, stream);
        writeByteArray(request, stream);
    }

    @Override
    public void deserializeBody(InputStream stream, TLContext context) throws IOException {
        throw new UnsupportedOperationException("Unable to deserialize invokeAfterMsg#cb9f372d");
    }
}
