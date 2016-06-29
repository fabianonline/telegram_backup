package org.telegram.mtproto.transport;

import org.telegram.mtproto.MTProto;
import org.telegram.mtproto.log.Logger;
import org.telegram.mtproto.util.BytesCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/**
 * Author: Korshakov Stepan
 * Created: 13.08.13 14:56
 */
public class TcpContext {
    private static final boolean LOG_OPERATIONS = false;
    private static final int MAX_PACKED_SIZE = 1024 * 1024 * 1024;//1 MB

    private class Package {
        public Package() {

        }

        private Package(byte[] data, boolean useFastConfirm) {
            this.data = data;
            this.useFastConfirm = useFastConfirm;
        }

        public byte[] data;
        public boolean useFastConfirm;
    }

    private final String TAG;

    private static final AtomicInteger contextLastId = new AtomicInteger(1);

    private static final int CONNECTION_TIMEOUT = 5 * 1000;

    private static final int READ_TIMEOUT = 1000;

    private static final int READ_DIE_TIMEOUT = 5 * 1000; // 5 sec

    private final String ip;
    private final int port;
    private final boolean useChecksum;

    private int sentPackets;
    private int receivedPackets;

    private boolean isClosed;
    private boolean isBroken;

    private Socket socket;

    private ReaderThread readerThread;

    private WriterThread writerThread;

    private DieThread dieThread;

    private TcpContextCallback callback;

    private final int contextId;

    private long lastWriteEvent = 0;

    public TcpContext(MTProto proto, String ip, int port, boolean checksum, TcpContextCallback callback) throws IOException {
        try {
            this.contextId = contextLastId.incrementAndGet();
            this.TAG = "MTProto#" + proto.getInstanceIndex() + "#Transport" + contextId;
            this.ip = ip;
            this.port = port;
            this.useChecksum = checksum;
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(ip, port), CONNECTION_TIMEOUT);
            this.socket.setKeepAlive(true);
            this.socket.setTcpNoDelay(true);
            if (!useChecksum) {
                socket.getOutputStream().write(0xef);
            }
            this.isClosed = false;
            this.isBroken = false;
            this.callback = callback;
            this.readerThread = new ReaderThread();
            this.writerThread = new WriterThread();
            this.dieThread = new DieThread();
            this.readerThread.start();
            this.writerThread.start();
            this.dieThread.start();
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException();
        }
    }

    public int getContextId() {
        return contextId;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public boolean isUseChecksum() {
        return useChecksum;
    }

    public int getSentPackets() {
        return sentPackets;
    }

    public int getReceivedPackets() {
        return receivedPackets;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public boolean isBroken() {
        return isBroken;
    }

    public void postMessage(byte[] data, boolean useFastConfirm) {
        writerThread.pushPackage(new Package(data, useFastConfirm));
    }

    public synchronized void close() {
        if (!isClosed) {
            Logger.w(TAG, "Manual context closing");
            isClosed = true;
            isBroken = false;
            try {
                readerThread.interrupt();
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
            try {
                writerThread.interrupt();
            } catch (Exception e) {
                Logger.e(TAG, e);
            }

            try {
                dieThread.interrupt();
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
    }

    private synchronized void onMessage(byte[] data, int len) {
        if (isClosed) {
            return;
        }

        callback.onRawMessage(data, 0, len, this);
    }

    private synchronized void onError(int errorCode) {
        if (isClosed) {
            return;
        }

        callback.onError(errorCode, this);
    }

    private synchronized void breakContext() {
        if (!isClosed) {
            Logger.w(TAG, "Breaking context");
            isClosed = true;
            isBroken = true;
            try {
                readerThread.interrupt();
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
            try {
                writerThread.interrupt();
            } catch (Exception e) {
                Logger.e(TAG, e);
            }

            try {
                dieThread.interrupt();
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }

        callback.onChannelBroken(this);
    }

    private class ReaderThread extends Thread {
        private ReaderThread() {
            setPriority(Thread.MIN_PRIORITY);
            setName(TAG + "#Reader" + hashCode());
        }

        @Override
        public void run() {
            try {
                while (!isClosed && !isInterrupted()) {
                    if (LOG_OPERATIONS) {
                        Logger.d(TAG, "Reader Iteration");
                    }
                    try {
                        if (socket.isClosed()) {
                            breakContext();
                            return;
                        }
                        if (!socket.isConnected()) {
                            breakContext();
                            return;
                        }

                        InputStream stream = socket.getInputStream();

                        byte[] pkg = null;
                        int pkgLen;
                        if (useChecksum) {
                            int length = readInt(stream);
                            if (arrayEq(intToBytes(length), "HTTP".getBytes())) {
                                Logger.d(TAG, "Received HTTP package");
                                breakContext();
                                return;
                            }
                            Logger.d(TAG, "Start reading message: " + length);
                            if (length <= 0) {
                                breakContext();
                                return;
                            }

                            if ((length >> 31) != 0) {
                                Logger.d(TAG, "fast confirm: " + Integer.toHexString(length));
                                callback.onFastConfirm(length);
                                continue;
                            }

                            if (length >= MAX_PACKED_SIZE) {
                                Logger.d(TAG, "Too big package");
                                breakContext();
                                return;
                            }

                            int packetIndex = readInt(stream);
                            if (length == 4) {
                                onError(packetIndex);
                                Logger.d(TAG, "Received error: " + packetIndex);
                                breakContext();
                                return;
                            }
                            pkgLen = length - 12;
                            pkg = readBytes(pkgLen, stream);
                            int readCrc = readInt(stream);
                            CRC32 crc32 = new CRC32();
                            crc32.update(intToBytes(length));
                            crc32.update(intToBytes(packetIndex));
                            crc32.update(pkg, 0, pkgLen);
                            if (readCrc != (int) crc32.getValue()) {
                                Logger.d(TAG, "Incorrect CRC");
                                breakContext();
                                return;
                            }

                            if (receivedPackets != packetIndex) {
                                Logger.d(TAG, "Incorrect packet index");
                                breakContext();
                                return;
                            }

                            receivedPackets++;
                        } else {
                            int length = readByte(stream);

                            Logger.d(TAG, "Start reading message: pre");

                            if (length >> 7 != 0) {
                                length = (length << 24) + (readByte(stream) << 16) + (readByte(stream) << 8) + (readByte(stream) << 0);
                                Logger.d(TAG, "fast confirm: " + Integer.toHexString(length));
                                callback.onFastConfirm(length);
                                continue;
                            } else {
                                if (length == 0x7F) {
                                    length = readByte(stream) + (readByte(stream) << 8) + (readByte(stream) << 16);
                                }
                                int len = length * 4;

                                Logger.d(TAG, "Start reading message: " + len);

                                if (length == 4) {
                                    int error = readInt(stream);
                                    onError(error);
                                    Logger.d(TAG, "Received error: " + error);
                                    breakContext();
                                    return;
                                }

                                if (length >= MAX_PACKED_SIZE) {
                                    Logger.d(TAG, "Too big package");
                                    breakContext();
                                    return;
                                }

                                pkgLen = len;
                                pkg = readBytes(len, READ_TIMEOUT, stream);
                            }
                        }
                        try {
                            onMessage(pkg, pkgLen);
                        } catch (Throwable t) {
                            Logger.e(TAG, t);
                            Logger.d(TAG, "Message processing error");
                            breakContext();
                            return;
                        } finally {
                            if (pkg != null) {
                                BytesCache.getInstance().put(pkg);
                            }
                        }
                    } catch (IOException e) {
                        Logger.e(TAG, e);
                        breakContext();
                        return;
                    }
                }
            } catch (Throwable e) {
                Logger.e(TAG, e);
                breakContext();
            }
        }
    }

    private class WriterThread extends Thread {
        private final ConcurrentLinkedQueue<Package> packages = new ConcurrentLinkedQueue<Package>();

        public WriterThread() {
            setPriority(Thread.MIN_PRIORITY);
            setName(TAG + "#Writer" + hashCode());
        }

        public void pushPackage(Package p) {
            packages.add(p);
            synchronized (packages) {
                packages.notifyAll();
            }
        }

        @Override
        public void run() {
            while (!isBroken) {
                if (LOG_OPERATIONS) {
                    Logger.d(TAG, "Writer Iteration");
                }
                Package p;
                synchronized (packages) {
                    p = packages.poll();
                    if (p == null) {
                        try {
                            packages.wait();
                        } catch (InterruptedException e) {
                            // e.printStackTrace();
                            return;
                        }
                        p = packages.poll();
                    }
                }
                if (p == null) {
                    if (isBroken) {
                        return;
                    } else {
                        continue;
                    }
                }

                if (LOG_OPERATIONS) {
                    Logger.d(TAG, "Writing data");
                }

                try {

                    byte[] data = p.data;
                    boolean useConfimFlag = p.useFastConfirm;

                    if (useChecksum) {
                        OutputStream stream = socket.getOutputStream();
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                        int len = data.length + 12;
                        if (useConfimFlag) {
                            len |= (1 << 31);
                        }
                        writeInt(len, outputStream);
                        writeInt(sentPackets, outputStream);
                        writeByteArray(data, outputStream);
                        CRC32 crc32 = new CRC32();
                        crc32.update(intToBytes(len));
                        crc32.update(intToBytes(sentPackets));
                        crc32.update(data);
                        writeInt((int) (crc32.getValue() & 0xFFFFFFFF), outputStream);
                        writeByteArray(outputStream.toByteArray(), stream);
                        onWrite();
                        stream.flush();
                    } else {
                        OutputStream stream = socket.getOutputStream();
                        if (useConfimFlag) {
                            if (data.length / 4 >= 0x7F) {
                                int len = data.length / 4;
                                writeByte(0xFF, stream);
                                writeByte(len & 0xFF, stream);
                                writeByte((len >> 8) & 0xFF, stream);
                                writeByte((len >> 16) & 0xFF, stream);
                            } else {
                                writeByte((data.length / 4) | (1 << 7), stream);
                            }
                        } else {
                            if (data.length / 4 >= 0x7F) {
                                int len = data.length / 4;
                                writeByte(0x7F, stream);
                                writeByte(len & 0xFF, stream);
                                writeByte((len >> 8) & 0xFF, stream);
                                writeByte((len >> 16) & 0xFF, stream);
                            } else {
                                writeByte(data.length / 4, stream);
                            }
                        }
                        writeByteArray(data, stream);
                        onWrite();
                        stream.flush();
                    }
                    sentPackets++;
                } catch (Exception e) {
                    Logger.e(TAG, e);
                    breakContext();
                }

                if (LOG_OPERATIONS) {
                    Logger.d(TAG, "End writing data");
                }
            }
        }
    }

    private void onWrite() {
        lastWriteEvent = System.nanoTime();
        notifyDieThread();
    }

    private void onRead() {
        lastWriteEvent = 0;
        notifyDieThread();
    }

    private void notifyDieThread() {
//        synchronized (dieThread) {
//            dieThread.notifyAll();
//        }
    }

    private class DieThread extends Thread {
        public DieThread() {
            setPriority(Thread.MIN_PRIORITY);
            setName(TAG + "#DieThread" + hashCode());
        }

        @Override
        public void run() {
            while (!isBroken) {
                if (Logger.LOG_THREADS) {
                    Logger.d(TAG, "DieThread iteration");
                }
                if (lastWriteEvent != 0) {
                    long delta = (System.nanoTime() - lastWriteEvent) / (1000 * 1000);
                    if (delta >= READ_DIE_TIMEOUT) {
                        Logger.d(TAG, "Dies by timeout");
                        breakContext();
                    } else {
                        try {
                            int waitDelta = (int) (READ_DIE_TIMEOUT - delta);
                            // Logger.d(TAG, "DieThread wait: " + waitDelta);
                            sleep(Math.max(waitDelta, 1000));
                            // Logger.d(TAG, "DieThread start wait end");
                        } catch (InterruptedException e) {
                            Logger.d(TAG, "DieThread exit");
                            return;
                        }
                    }
                } else {
                    try {
                        // Logger.d(TAG, "DieThread start common wait");
                        sleep(READ_DIE_TIMEOUT);
                        // Logger.d(TAG, "DieThread end common wait");
                    } catch (InterruptedException e) {
                        Logger.d(TAG, "DieThread exit");
                        return;
                    }
                }
            }
            Logger.d(TAG, "DieThread exit");
        }
    }


    private void writeByteArray(byte[] data, OutputStream stream) throws IOException {
        stream.write(data);
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)};
    }

    private void writeInt(int value, OutputStream stream) throws IOException {
        stream.write((byte) (value & 0xFF));
        stream.write((byte) ((value >> 8) & 0xFF));
        stream.write((byte) ((value >> 16) & 0xFF));
        stream.write((byte) ((value >> 24) & 0xFF));
    }

    private void writeByte(int v, OutputStream stream) throws IOException {
        stream.write(v);
    }

    private void writeByte(byte v, OutputStream stream) throws IOException {
        stream.write(v);
    }

    private byte[] readBytes(int count, int timeout, InputStream stream) throws IOException {
        byte[] res = BytesCache.getInstance().allocate(count);
        int offset = 0;
        long start = System.nanoTime();
        while (offset < count) {
            int readed = stream.read(res, offset, count - offset);
            Thread.yield();
            if (readed > 0) {
                offset += readed;
                onRead();
            } else if (readed < 0) {
                throw new IOException();
            } else {
                if (System.nanoTime() - start > timeout * 1000000L) {
                    throw new IOException();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Logger.e(TAG, e);
                    throw new IOException();
                }
            }
        }
        return res;
    }

    private byte[] readBytes(int count, InputStream stream) throws IOException {
        byte[] res = BytesCache.getInstance().allocate(count);
        int offset = 0;
        while (offset < count) {
            int readed = stream.read(res, offset, count - offset);
            Thread.yield();
            if (readed > 0) {
                offset += readed;
                onRead();
            } else if (readed < 0) {
                throw new IOException();
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Logger.e(TAG, e);
                    throw new IOException();
                }
            }
        }
        return res;
    }

    private int readInt(InputStream stream) throws IOException {
        int a = stream.read();
        if (a < 0) {
            throw new IOException();
        }
        onRead();

        int b = stream.read();
        if (b < 0) {
            throw new IOException();
        }
        onRead();

        int c = stream.read();
        if (c < 0) {
            throw new IOException();
        }
        onRead();

        int d = stream.read();
        if (d < 0) {
            throw new IOException();
        }
        onRead();

        return a + (b << 8) + (c << 16) + (d << 24);
    }

    private int readInt(byte[] src) {
        return readInt(src, 0);
    }

    private int readInt(byte[] src, int offset) {
        int a = src[offset + 0] & 0xFF;
        int b = src[offset + 1] & 0xFF;
        int c = src[offset + 2] & 0xFF;
        int d = src[offset + 3] & 0xFF;

        return a + (b << 8) + (c << 16) + (d << 24);
    }

    private int readByte(InputStream stream) throws IOException {
        int res = stream.read();
        if (res < 0) {
            throw new IOException();
        }
        onRead();
        return res;
    }

    private boolean arrayEq(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i])
                return false;
        }
        return true;
    }
}