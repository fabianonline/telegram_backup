package org.telegram.mtproto;

import org.telegram.tl.TLMethod;
import org.telegram.tl.TLObject;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 07.11.13
 * Time: 3:56
 */
public interface CallWrapper {
    public TLObject wrapObject(TLMethod srcRequest);
}
