package org.telegram.api.engine;

import org.telegram.tl.TLObject;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 05.11.13
 * Time: 14:10
 */
public interface RpcCallback<T extends TLObject> {
    public void onResult(T result);

    public void onError(int errorCode, String message);
}
