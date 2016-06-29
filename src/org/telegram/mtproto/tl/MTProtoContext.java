package org.telegram.mtproto.tl;

import org.telegram.tl.TLContext;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 8:22
 */
public class MTProtoContext extends TLContext {

    // High performance singleton
    private static class ContextHolder {
        public static final MTProtoContext HOLDER_INSTANCE = new MTProtoContext();
    }

    public static MTProtoContext getInstance() {
        return ContextHolder.HOLDER_INSTANCE;
    }

    private MTProtoContext() {

    }

    @Override
    protected void init() {
        registerClass(MTPing.CLASS_ID, MTPing.class);
        registerClass(MTPingDelayDisconnect.CLASS_ID, MTPingDelayDisconnect.class);
        registerClass(MTPong.CLASS_ID, MTPong.class);
        registerClass(MTMsgsAck.CLASS_ID, MTMsgsAck.class);
        registerClass(MTNewSessionCreated.CLASS_ID, MTNewSessionCreated.class);
        registerClass(MTBadMessageNotification.CLASS_ID, MTBadMessageNotification.class);
        registerClass(MTBadServerSalt.CLASS_ID, MTBadServerSalt.class);
        registerClass(MTNewMessageDetailedInfo.CLASS_ID, MTNewMessageDetailedInfo.class);
        registerClass(MTMessageDetailedInfo.CLASS_ID, MTMessageDetailedInfo.class);
        registerClass(MTNeedResendMessage.CLASS_ID, MTNeedResendMessage.class);
        registerClass(MTMessagesContainer.CLASS_ID, MTMessagesContainer.class);
        registerClass(MTRpcError.CLASS_ID, MTRpcError.class);
        registerClass(MTRpcResult.CLASS_ID, MTRpcResult.class);
        registerClass(MTGetFutureSalts.CLASS_ID, MTGetFutureSalts.class);
        registerClass(MTFutureSalt.CLASS_ID, MTFutureSalt.class);
        registerClass(MTFutureSalts.CLASS_ID, MTFutureSalts.class);
    }
}
