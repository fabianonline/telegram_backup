package org.telegram.mtproto.transport;

import org.telegram.mtproto.tl.MTMessage;

/**
 * Created by ex3ndr on 03.04.14.
 */
public interface TransportPoolCallback {
    public void onMTMessage(MTMessage message);

    public void onFastConfirm(int hash);
}
