package org.telegram.mtproto.transport;

/**
 * Author: Korshakov Stepan
 * Created: 13.08.13 15:35
 */
public interface TcpContextCallback {
    public void onRawMessage(byte[] data, int offset, int len, TcpContext context);

    public void onError(int errorCode, TcpContext context);

    public void onChannelBroken(TcpContext context);

    public void onFastConfirm(int hash);
}
