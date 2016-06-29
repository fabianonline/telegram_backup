package org.telegram.mtproto.transport;

import org.telegram.mtproto.log.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.telegram.tl.StreamingUtils.readBytes;
import static org.telegram.tl.StreamingUtils.writeByte;
import static org.telegram.tl.StreamingUtils.writeByteArray;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 5:04
 */
public class PlainTcpConnection {
    private static final String TAG = "PlainTcpConnection";

    private static final int CONNECTION_TIMEOUT = 5 * 1000;

    private Socket socket;

    private boolean isBroken;

    public PlainTcpConnection(String ip, int port) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(ip, port), CONNECTION_TIMEOUT);
        this.socket.setKeepAlive(true);
        this.socket.setTcpNoDelay(true);
        this.socket.getOutputStream().write(0xef);
        this.isBroken = false;
    }

    public Socket getSocket() {
        return socket;
    }

    private byte[] readMessage() throws IOException {
        InputStream stream = socket.getInputStream();
        int headerLen = readByte(stream);

        if (headerLen == 0x7F) {
            headerLen = readByte(stream) + (readByte(stream) << 8) + (readByte(stream) << 16);
        }
        int len = headerLen * 4;
        return readBytes(len, stream);
    }

    private void writeMessage(byte[] request) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        if (request.length / 4 >= 0x7F) {
            int len = request.length / 4;
            writeByte(0x7F, stream);
            writeByte(len & 0xFF, stream);
            writeByte((len >> 8) & 0xFF, stream);
            writeByte((len >> 16) & 0xFF, stream);
        } else {
            writeByte(request.length / 4, stream);
        }
        writeByteArray(request, stream);
        byte[] pkg = stream.toByteArray();
        socket.getOutputStream().write(pkg, 0, pkg.length);
        socket.getOutputStream().flush();
    }

    public byte[] executeMethod(byte[] request) throws IOException {
        writeMessage(request);
        return readMessage();
    }

    public void destroy() {
        try {
            socket.close();
        } catch (IOException e) {
            Logger.e(TAG, e);
        }
    }

    private int readByte(InputStream stream) throws IOException {
        int res = stream.read();
        if (res < 0) {
            throw new IOException();
        }
        return res;
    }
}
