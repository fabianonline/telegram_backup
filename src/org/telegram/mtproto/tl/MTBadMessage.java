package org.telegram.mtproto.tl;

import org.telegram.tl.TLObject;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 21:40
 */
public abstract class MTBadMessage extends TLObject {
    protected long badMsgId;
    protected int badMsqSeqno;
    protected int errorCode;

    public long getBadMsgId() {
        return badMsgId;
    }

    public int getBadMsqSeqno() {
        return badMsqSeqno;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
