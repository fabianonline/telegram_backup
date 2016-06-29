package org.telegram.mtproto.tl.pq;

import org.telegram.tl.TLContext;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 5:29
 */
public class TLInitContext extends TLContext {
    @Override
    protected void init() {
        registerClass(ReqPQ.CLASS_ID, ReqPQ.class);
        registerClass(ResPQ.CLASS_ID, ResPQ.class);
        registerClass(ReqDhParams.CLASS_ID, ReqDhParams.class);
        registerClass(ServerDhOk.CLASS_ID, ServerDhOk.class);
        registerClass(ServerDhFailure.CLASS_ID, ServerDhFailure.class);
        registerClass(ServerDhInner.CLASS_ID, ServerDhInner.class);
        registerClass(DhGenOk.CLASS_ID, DhGenOk.class);
        registerClass(DhGenFailure.CLASS_ID, DhGenFailure.class);
        registerClass(DhGenRetry.CLASS_ID, DhGenRetry.class);
        registerClass(ReqSetDhClientParams.CLASS_ID, ReqSetDhClientParams.class);
        registerClass(ClientDhInner.CLASS_ID, ClientDhInner.class);
    }
}
