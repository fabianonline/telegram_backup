package org.telegram.mtproto.pq;

import org.telegram.mtproto.ServerException;
import org.telegram.mtproto.TransportSecurityException;
import org.telegram.mtproto.log.Logger;
import org.telegram.mtproto.secure.CryptoUtils;
import org.telegram.mtproto.secure.Entropy;
import org.telegram.mtproto.secure.Keys;
import org.telegram.mtproto.secure.pq.PQSolver;
import org.telegram.mtproto.state.ConnectionInfo;
import org.telegram.mtproto.time.TimeOverlord;
import org.telegram.mtproto.tl.pq.*;
import org.telegram.mtproto.transport.ConnectionType;
import org.telegram.mtproto.transport.PlainTcpConnection;
import org.telegram.mtproto.transport.TransportRate;
import org.telegram.tl.TLMethod;
import org.telegram.tl.TLObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

import static org.telegram.mtproto.secure.CryptoUtils.*;
import static org.telegram.tl.StreamingUtils.*;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 4:11
 */
public class Authorizer {
    private static final String TAG = "Authorizer";
    private static final int AUTH_ATTEMPT_COUNT = 5;
    private static final int AUTH_RETRY_COUNT = 5;

    private PlainTcpConnection context;
    private TLInitContext initContext;

    public Authorizer() {
        initContext = new TLInitContext();
    }

    private <T extends TLObject> T executeMethod(TLMethod<T> object) throws IOException {
        long requestMessageId = TimeOverlord.getInstance().createWeakMessageId();
        long start = System.nanoTime();
        byte[] data = object.serialize();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLong(0, out); // Empty AUTH_ID
        writeLong(requestMessageId, out); // MessageID
        writeInt(data.length, out);
        writeByteArray(data, out);
        byte[] response = context.executeMethod(out.toByteArray());
        ByteArrayInputStream in = new ByteArrayInputStream(response);
        long authId = readLong(in);
        if (authId != 0) {
            throw new IOException("Auth id might be equal to zero");
        }
        long messageId = readLong(in);
        // TimeOverlord.getInstance().onMethodExecuted(requestMessageId, messageId, (System.nanoTime() - start) / 1000000);
        int length = readInt(in);
        byte[] messageResponse = readBytes(length, in);
        return object.deserializeResponse(messageResponse, initContext);
    }

    private PqAuth authAttempt() throws IOException {
        // PQ-Auth start
        byte[] nonce = Entropy.generateSeed(16);
        ResPQ resPQ = executeMethod(new ReqPQ(nonce));
        byte[] serverNonce = resPQ.getServerNonce();

        long fingerprint = 0;
        Keys.Key publicKey = null;
        outer:
        for (Long srcFingerprint : resPQ.getFingerprints()) {
            for (Keys.Key key : Keys.AVAILABLE_KEYS) {
                if (srcFingerprint.equals(key.getFingerprint())) {
                    fingerprint = srcFingerprint;
                    publicKey = key;
                    break outer;
                }
            }
        }

        if (fingerprint == 0) {
            throw new IOException("Unknown public keys");
        }

        BigInteger pq = loadBigInt(resPQ.getPq());
        BigInteger p = null;
        try {
            long start = System.currentTimeMillis();
            p = PQSolver.solvePq(pq);
            Logger.d(TAG, "Solved PQ in " + (System.currentTimeMillis() - start) + " ms");
        } catch (Exception e) {
            throw new IOException();
        }
        BigInteger q = pq.divide(p);

        byte[] newNonce = Entropy.generateSeed(32);

        PQInner inner = new PQInner(resPQ.getPq(), fromBigInt(p), fromBigInt(q), nonce,
                serverNonce, newNonce);

        byte[] pqInner = inner.serialize();

        // PQ INNER
        byte[] hash = CryptoUtils.SHA1(pqInner);
        byte[] seed = Entropy.generateSeed(255 - hash.length - pqInner.length);
        byte[] dataWithHash = concat(hash, pqInner, seed);

        byte[] encrypted = CryptoUtils.RSA(dataWithHash, publicKey.getPublicKey(), publicKey.getExponent());

        long start = System.nanoTime();
        ServerDhParams dhParams = executeMethod(new ReqDhParams(nonce, serverNonce, fromBigInt(p), fromBigInt(q),
                fingerprint, encrypted));
        long dhParamsDuration = (System.nanoTime() - start) / (1000 * 1000);

        if (dhParams instanceof ServerDhFailure) {
            ServerDhFailure hdFailure = (ServerDhFailure) dhParams;
            if (arrayEq(hdFailure.getNewNonceHash(), SHA1(newNonce))) {
                throw new ServerException("Received server_DH_params_fail#79cb045d");
            } else {
                throw new TransportSecurityException("Received server_DH_params_fail#79cb045d with incorrect hash");
            }
        }

        // PQ-Auth end
        // DH-Auth start
        ServerDhOk serverDhParams = (ServerDhOk) dhParams;

        byte[] encryptedAnswer = serverDhParams.getEncryptedAnswer();

        byte[] tmpAesKey = concat(SHA1(newNonce, serverNonce), substring(SHA1(serverNonce, newNonce), 0, 12));
        byte[] tmpAesIv = concat(concat(substring(SHA1(serverNonce, newNonce), 12, 8), SHA1(newNonce, newNonce)),
                substring(newNonce, 0, 4));

        byte[] answer = AES256IGEDecrypt(encryptedAnswer, tmpAesIv, tmpAesKey);
        ByteArrayInputStream stream = new ByteArrayInputStream(answer);
        byte[] answerHash = readBytes(20, stream); // Hash
        ServerDhInner dhInner = (ServerDhInner) initContext.deserializeMessage(stream);
        if (!arrayEq(answerHash, SHA1(dhInner.serialize()))) {
            throw new TransportSecurityException();
        }

        TimeOverlord.getInstance().onServerTimeArrived(dhInner.getServerTime() * 1000L, dhParamsDuration);

        for (int i = 0; i < AUTH_RETRY_COUNT; i++) {
            BigInteger b = loadBigInt(Entropy.generateSeed(256));
            BigInteger g = new BigInteger(dhInner.getG() + "");
            BigInteger dhPrime = loadBigInt(dhInner.getDhPrime());
            BigInteger gb = g.modPow(b, dhPrime);

            BigInteger authKeyVal = loadBigInt(dhInner.getG_a()).modPow(b, dhPrime);
            byte[] authKey = alignKeyZero(fromBigInt(authKeyVal), 256);
            byte[] authAuxHash = substring(SHA1(authKey), 0, 8);

            ClientDhInner clientDHInner = new ClientDhInner(nonce, serverNonce, i, fromBigInt(gb));
            byte[] innerData = clientDHInner.serialize();
            byte[] innerDataWithHash = align(concat(SHA1(innerData), innerData), 16);
            byte[] dataWithHashEnc = AES256IGEEncrypt(innerDataWithHash, tmpAesIv, tmpAesKey);

            DhGenResult result = executeMethod(new ReqSetDhClientParams(nonce, serverNonce, dataWithHashEnc));

            if (result instanceof DhGenOk) {
                byte[] newNonceHash = substring(SHA1(newNonce, new byte[]{1}, authAuxHash), 4, 16);

                if (!arrayEq(result.getNewNonceHash(), newNonceHash))
                    throw new TransportSecurityException();

                long serverSalt = readLong(xor(substring(newNonce, 0, 8), substring(serverNonce, 0, 8)), 0);

                return new PqAuth(authKey, serverSalt, context.getSocket());
            } else if (result instanceof DhGenRetry) {
                byte[] newNonceHash = substring(SHA1(newNonce, new byte[]{2}, authAuxHash), 4, 16);

                if (!arrayEq(result.getNewNonceHash(), newNonceHash))
                    throw new TransportSecurityException();

            } else if (result instanceof DhGenFailure) {
                byte[] newNonceHash = substring(SHA1(newNonce, new byte[]{3}, authAuxHash), 4, 16);

                if (!arrayEq(result.getNewNonceHash(), newNonceHash))
                    throw new TransportSecurityException();

                throw new ServerException();
            }
        }
        throw new ServerException();
    }

    public PqAuth doAuth(ConnectionInfo[] infos) {
        TransportRate rate = new TransportRate(infos);
        for (int i = 0; i < AUTH_ATTEMPT_COUNT; i++) {
            ConnectionType connectionType = rate.tryConnection();
            try {
                context = new PlainTcpConnection(connectionType.getHost(), connectionType.getPort());
                rate.onConnectionSuccess(connectionType.getId());
            } catch (IOException e) {
                Logger.e(TAG, e);
                rate.onConnectionFailure(connectionType.getId());
                continue;
            }

            try {
                return authAttempt();
            } catch (IOException e) {
                Logger.e(TAG, e);
            } finally {
                if (context != null) {
                    context.destroy();
                    context = null;
                }
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                return null;
            }
        }
        return null;
    }
}
