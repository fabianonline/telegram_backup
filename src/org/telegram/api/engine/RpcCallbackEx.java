package org.telegram.api.engine;

import org.telegram.tl.TLObject;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 09.11.13
 * Time: 18:06
 */
public interface RpcCallbackEx<T extends TLObject> extends RpcCallback<T> {
    public void onConfirmed();
}
