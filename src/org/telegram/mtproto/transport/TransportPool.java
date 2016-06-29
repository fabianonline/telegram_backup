package org.telegram.mtproto.transport;

import org.telegram.mtproto.MTProto;
import org.telegram.mtproto.schedule.Scheduller;
import org.telegram.mtproto.schedule.SchedullerListener;
import org.telegram.mtproto.state.AbsMTProtoState;
import org.telegram.mtproto.time.TimeOverlord;
import org.telegram.mtproto.tl.MTMessage;
import org.telegram.mtproto.tl.MTMessagesContainer;
import org.telegram.mtproto.tl.MTProtoContext;
import org.telegram.mtproto.util.BytesCache;
import org.telegram.tl.StreamingUtils;
import org.telegram.tl.TLObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.telegram.mtproto.secure.CryptoUtils.*;
import static org.telegram.mtproto.secure.CryptoUtils.align;
import static org.telegram.tl.StreamingUtils.*;
import static org.telegram.tl.StreamingUtils.writeByteArray;

/**
 * Created by ex3ndr on 03.04.14.
 */
public abstract class TransportPool implements SchedullerListener {

    public static final int MODE_DEFAULT = 0;
    public static final int MODE_LOWMODE = 1;

    protected MTProto proto;
    protected AbsMTProtoState state;
    protected Scheduller scheduller;
    protected int mode = MODE_DEFAULT;
    protected MTProtoContext context;
    private TransportPoolCallback transportPoolCallback;

    private byte[] authKey;
    private byte[] authKeyId;
    private byte[] session;

    protected boolean isClosed = false;

    public TransportPool(MTProto proto, TransportPoolCallback transportPoolCallback) {
        this.proto = proto;
        this.scheduller = proto.getScheduller();
        this.scheduller.addListener(this);
        this.transportPoolCallback = transportPoolCallback;
        this.context = MTProtoContext.getInstance();
        this.authKey = proto.getAuthKey();
        this.authKeyId = proto.getAuthKeyId();
        this.session = proto.getSession();
        this.state = proto.getState();
    }

    public synchronized void close() {
        isClosed = true;
    }

    public void switchMode(int mode) {
        if (this.mode != mode) {
            this.mode = mode;
            onModeChanged();
        }
    }

    protected void onModeChanged() {

    }

    public void onSessionChanged(byte[] session) {
        if (isClosed) {
            return;
        }
        this.session = session;
    }

    public abstract void reloadConnectionInformation();

    public abstract void resetConnectionBackoff();

    protected synchronized void onMTMessage(MTMessage message) {
        if (isClosed) {
            return;
        }
        if (readInt(message.getContent()) == MTMessagesContainer.CLASS_ID) {
            try {
                TLObject object = context.deserializeMessage(new ByteArrayInputStream(message.getContent()));
                if (object instanceof MTMessagesContainer) {
                    MTMessagesContainer container = (MTMessagesContainer) object;
                    for (MTMessage mtMessage : container.getMessages()) {
                        transportPoolCallback.onMTMessage(mtMessage);
                    }
                }
            } catch (IOException e) {
                // Ignore this
                // Logger.e(TAG, e);
            } finally {
                BytesCache.getInstance().put(message.getContent());
            }
        } else {
            transportPoolCallback.onMTMessage(message);
        }

    }

    protected synchronized void onFastConfirm(int hash) {
        if (isClosed) {
            return;
        }
        transportPoolCallback.onFastConfirm(hash);
    }

    @Override
    public void onSchedullerUpdated(Scheduller scheduller) {

    }


    private byte[] optimizedSHA(byte[] serverSalt, byte[] session, long msgId, int seq, int len, byte[] data, int datalen) {
        try {
            MessageDigest crypt = MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(serverSalt);
            crypt.update(session);
            crypt.update(longToBytes(msgId));
            crypt.update(intToBytes(seq));
            crypt.update(intToBytes(len));
            crypt.update(data, 0, datalen);
            return crypt.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return null;
    }

    protected MTMessage decrypt(byte[] data, int offset, int len) throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(data);
        stream.skip(offset);
        byte[] msgAuthKey = readBytes(8, stream);
        for (int i = 0; i < authKeyId.length; i++) {
            if (msgAuthKey[i] != authKeyId[i]) {
                // Logger.w(TAG, "Unsupported msgAuthKey");
                throw new SecurityException();
            }
        }
        byte[] msgKey = readBytes(16, stream);

        byte[] sha1_a = SHA1(msgKey, substring(authKey, 8, 32));
        byte[] sha1_b = SHA1(substring(authKey, 40, 16), msgKey, substring(authKey, 56, 16));
        byte[] sha1_c = SHA1(substring(authKey, 72, 32), msgKey);
        byte[] sha1_d = SHA1(msgKey, substring(authKey, 104, 32));

        byte[] aesKey = concat(substring(sha1_a, 0, 8), substring(sha1_b, 8, 12), substring(sha1_c, 4, 12));
        byte[] aesIv = concat(substring(sha1_a, 8, 12), substring(sha1_b, 0, 8), substring(sha1_c, 16, 4), substring(sha1_d, 0, 8));

        int totalLen = len - 8 - 16;
        byte[] encMessage = BytesCache.getInstance().allocate(totalLen);
        readBytes(encMessage, 0, totalLen, stream);

        byte[] rawMessage = BytesCache.getInstance().allocate(totalLen);
        // long decryptStart = System.currentTimeMillis();
        AES256IGEDecryptBig(encMessage, rawMessage, totalLen, aesIv, aesKey);
        // Logger.d(TAG, "Decrypted in " + (System.currentTimeMillis() - decryptStart) + " ms");
        BytesCache.getInstance().put(encMessage);

        ByteArrayInputStream bodyStream = new ByteArrayInputStream(rawMessage);
        byte[] serverSalt = readBytes(8, bodyStream);
        byte[] session = readBytes(8, bodyStream);
        long messageId = readLong(bodyStream);
        int mes_seq = StreamingUtils.readInt(bodyStream);

        int msg_len = StreamingUtils.readInt(bodyStream);

        int bodySize = totalLen - 32;

        if (msg_len % 4 != 0) {
            throw new SecurityException();
        }

        if (msg_len > bodySize) {
            throw new SecurityException();
        }

        if (msg_len - bodySize > 15) {
            throw new SecurityException();
        }

        byte[] message = BytesCache.getInstance().allocate(msg_len);
        readBytes(message, 0, msg_len, bodyStream);

        BytesCache.getInstance().put(rawMessage);

        byte[] checkHash = optimizedSHA(serverSalt, session, messageId, mes_seq, msg_len, message, msg_len);

        if (!arrayEq(substring(checkHash, 4, 16), msgKey)) {
            throw new SecurityException();
        }

        if (!arrayEq(session, this.session)) {
            return null;
        }

//        if (TimeOverlord.getInstance().getTimeAccuracy() < 10) {
//            long time = (messageId >> 32);
//            long serverTime = TimeOverlord.getInstance().getServerTime();
//
//            if (serverTime + 30 < time) {
//                Logger.w(TAG, "Incorrect message time: " + time + " with server time: " + serverTime);
//                // return null;
//            }
//
//            if (time < serverTime - 300) {
//                Logger.w(TAG, "Incorrect message time: " + time + " with server time: " + serverTime);
//                // return null;
//            }
//        }

        return new MTMessage(messageId, mes_seq, message, message.length);
    }

    protected EncryptedMessage encrypt(int seqNo, long messageId, byte[] content) throws IOException {
        long salt = state.findActualSalt((int) (TimeOverlord.getInstance().getServerTime() / 1000));
        ByteArrayOutputStream messageBody = new ByteArrayOutputStream();
        writeLong(salt, messageBody);
        writeByteArray(session, messageBody);
        writeLong(messageId, messageBody);
        writeInt(seqNo, messageBody);
        writeInt(content.length, messageBody);
        writeByteArray(content, messageBody);

        byte[] innerData = messageBody.toByteArray();
        byte[] msgKey = substring(SHA1(innerData), 4, 16);
        int fastConfirm = readInt(SHA1(innerData)) | (1 << 31);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeByteArray(authKeyId, out);
        writeByteArray(msgKey, out);

        byte[] sha1_a = SHA1(msgKey, substring(authKey, 0, 32));
        byte[] sha1_b = SHA1(substring(authKey, 32, 16), msgKey, substring(authKey, 48, 16));
        byte[] sha1_c = SHA1(substring(authKey, 64, 32), msgKey);
        byte[] sha1_d = SHA1(msgKey, substring(authKey, 96, 32));

        byte[] aesKey = concat(substring(sha1_a, 0, 8), substring(sha1_b, 8, 12), substring(sha1_c, 4, 12));
        byte[] aesIv = concat(substring(sha1_a, 8, 12), substring(sha1_b, 0, 8), substring(sha1_c, 16, 4), substring(sha1_d, 0, 8));

        byte[] encoded = AES256IGEEncrypt(align(innerData, 16), aesIv, aesKey);
        writeByteArray(encoded, out);
        EncryptedMessage res = new EncryptedMessage();
        res.data = out.toByteArray();
        res.fastConfirm = fastConfirm;
        return res;
    }

    protected class EncryptedMessage {
        public byte[] data;
        public int fastConfirm;
    }
}
