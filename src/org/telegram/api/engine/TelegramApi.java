package org.telegram.api.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.telegram.api.TLAbsUpdates;
import org.telegram.api.TLApiContext;
import org.telegram.api.TLConfig;
import org.telegram.api.auth.TLExportedAuthorization;
import org.telegram.api.engine.file.Downloader;
import org.telegram.api.engine.file.Uploader;
import org.telegram.api.engine.storage.AbsApiState;
import org.telegram.api.requests.TLRequestAuthExportAuthorization;
import org.telegram.api.requests.TLRequestAuthImportAuthorization;
import org.telegram.api.requests.TLRequestHelpGetConfig;
import org.telegram.api.requests.TLRequestInitConnection;
import org.telegram.api.requests.TLRequestInvokeWithLayer;
import org.telegram.api.requests.TLRequestUploadGetFile;
import org.telegram.api.requests.TLRequestUploadSaveBigFilePart;
import org.telegram.api.requests.TLRequestUploadSaveFilePart;
import org.telegram.api.upload.TLFile;
import org.telegram.mtproto.CallWrapper;
import org.telegram.mtproto.MTProto;
import org.telegram.mtproto.MTProtoCallback;
import org.telegram.mtproto.pq.Authorizer;
import org.telegram.mtproto.pq.PqAuth;
import org.telegram.mtproto.state.ConnectionInfo;
import org.telegram.mtproto.util.BytesCache;
import org.telegram.tl.TLBool;
import org.telegram.tl.TLBoolTrue;
import org.telegram.tl.TLBytes;
import org.telegram.tl.TLMethod;
import org.telegram.tl.TLObject;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 04.11.13
 * Time: 21:54
 */
public class TelegramApi {

    private static final AtomicInteger rpcCallIndex = new AtomicInteger(0);

    private static final AtomicInteger instanceIndex = new AtomicInteger(1000);

    private final String TAG;

    private final int INSTANCE_INDEX;

    private static final int CHANNELS_MAIN = 1;
    private static final int CHANNELS_FS = 2;

    private static final int DEFAULT_TIMEOUT_CHECK = 15000;
    private static final int DEFAULT_TIMEOUT = 15000;
    private static final int FILE_TIMEOUT = 45000;

    private boolean isClosed;

    private int primaryDc;

    private MTProto mainProto;

    private final HashMap<Integer, MTProto> dcProtos = new HashMap<Integer, MTProto>();
    private final HashMap<Integer, Object> dcSync = new HashMap<Integer, Object>();

    private ProtoCallback callback;

    private SenderThread senderThread;

    private final HashMap<Integer, RpcCallbackWrapper> callbacks = new HashMap<Integer, RpcCallbackWrapper>();
    private final HashMap<Integer, Integer> sentRequests = new HashMap<Integer, Integer>();

    private TLApiContext apiContext;

    private TimeoutThread timeoutThread;
    private final TreeMap<Long, Integer> timeoutTimes = new TreeMap<Long, Integer>();

    private ConnectionThread dcThread;
    private final TreeMap<Integer, Boolean> dcRequired = new TreeMap<Integer, Boolean>();

    private HashSet<Integer> registeredInApi = new HashSet<Integer>();

    private AbsApiState state;
    private AppInfo appInfo;

    private ApiCallback apiCallback;

    private Downloader downloader;

    private Uploader uploader;

    public TelegramApi(AbsApiState state, AppInfo _appInfo, ApiCallback _apiCallback) {
        this.INSTANCE_INDEX = instanceIndex.incrementAndGet();
        this.TAG = "TelegramApi#" + INSTANCE_INDEX;

        long start = System.currentTimeMillis();
        this.apiCallback = _apiCallback;
        this.appInfo = _appInfo;
        this.state = state;
        this.primaryDc = state.getPrimaryDc();
        this.isClosed = false;
        this.callback = new ProtoCallback();
        Logger.d(TAG, "Phase 0 in " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        this.apiContext = new TLApiContext() {
            private AtomicInteger integer = new AtomicInteger(0);

            @Override
            public TLObject deserializeMessage(int clazzId, InputStream stream) throws IOException {
                if (integer.incrementAndGet() % 10 == 9) {
                    Thread.yield();
                }
                return super.deserializeMessage(clazzId, stream);
            }

            @Override
            public TLBytes allocateBytes(int size) {
                return new TLBytes(BytesCache.getInstance().allocate(size), 0, size);
            }

            @Override
            public void releaseBytes(TLBytes unused) {
                BytesCache.getInstance().put(unused.getData());
            }
        };

        Logger.d(TAG, "Phase 1 in " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        this.timeoutThread = new TimeoutThread();
        this.timeoutThread.start();

        this.dcThread = new ConnectionThread();
        this.dcThread.start();

        this.senderThread = new SenderThread();
        this.senderThread.start();
        Logger.d(TAG, "Phase 2 in " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        this.downloader = new Downloader(this);
        this.uploader = new Uploader(this);
        Logger.d(TAG, "Phase 3 in " + (System.currentTimeMillis() - start) + " ms");
    }

    public Downloader getDownloader() {
        return downloader;
    }

    public Uploader getUploader() {
        return uploader;
    }

    public void switchToDc(int dcId) {
        if (this.mainProto != null) {
            this.mainProto.close();
        }
        this.mainProto = null;
        this.primaryDc = dcId;
        this.state.setPrimaryDc(dcId);
        synchronized (dcRequired) {
            dcRequired.notifyAll();
        }
    }

    @Override
    public String toString() {
        return "api#" + INSTANCE_INDEX;
    }

    private TLMethod wrapForDc(int dcId, TLMethod method) {
		if (!registeredInApi.contains(dcId)) {
			method = new TLRequestInitConnection(appInfo.getApiId(), appInfo.getDeviceModel(),
					appInfo.getSystemVersion(), appInfo.getAppVersion(), appInfo.getLangCode(), method);
        }
		return new TLRequestInvokeWithLayer(23, method);
    }

    public AbsApiState getState() {
        return state;
    }

    public TLApiContext getApiContext() {
        return apiContext;
    }

    protected void onMessageArrived(TLObject object) {
        if (object instanceof TLAbsUpdates) {
            Logger.d(TAG, "<< update " + object.toString());
            apiCallback.onUpdate((TLAbsUpdates) object);
        } else {
            Logger.d(TAG, "<< unknown object " + object.toString());
        }
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void close() {
        if (!this.isClosed) {
            apiCallback.onAuthCancelled(this);
            this.isClosed = true;
            if (this.timeoutThread != null) {
                this.timeoutThread.interrupt();
                this.timeoutThread = null;
            }
            mainProto.close();
        }
    }

    public void resetNetworkBackoff() {
        if (mainProto != null) {
            mainProto.resetNetworkBackoff();
        }
        for (MTProto mtProto : dcProtos.values()) {
            mtProto.resetNetworkBackoff();
        }
    }

    public void resetConnectionInfo() {
        mainProto.reloadConnectionInformation();
        synchronized (dcProtos) {
            for (MTProto proto : dcProtos.values()) {
                proto.reloadConnectionInformation();
            }
        }
    }

    // Basic sync and async methods

    private <T extends TLObject> void doRpcCall(TLMethod<T> method, int timeout, RpcCallback<T> callback, int destDc) {
        doRpcCall(method, timeout, callback, destDc, true);
    }

    private <T extends TLObject> void doRpcCall(TLMethod<T> method, int timeout, RpcCallback<T> callback, int destDc,
                                                boolean authRequired) {
        if (isClosed) {
            if (callback != null) {
                callback.onError(0, null);
            }
            return;
        }
        int localRpcId = rpcCallIndex.getAndIncrement();
        synchronized (callbacks) {
            RpcCallbackWrapper wrapper = new RpcCallbackWrapper(localRpcId, method, callback);
            wrapper.dcId = destDc;
            wrapper.timeout = timeout;
            wrapper.isAuthRequred = authRequired;

            callbacks.put(localRpcId, wrapper);

            if (callback != null) {
                long timeoutTime = System.nanoTime() + timeout * 1000 * 1000L;
                synchronized (timeoutTimes) {
                    while (timeoutTimes.containsKey(timeoutTime)) {
                        timeoutTime++;
                    }
                    timeoutTimes.put(timeoutTime, localRpcId);
                    timeoutTimes.notifyAll();
                }
                wrapper.timeoutTime = timeoutTime;
            }

            if (authRequired) {
                checkDcAuth(destDc);
            } else {
                checkDc(destDc);
            }

            callbacks.notifyAll();
        }

        Logger.d(TAG, ">> #" + +localRpcId + ": " + method.toString());
    }

    private <T extends TLObject> T doRpcCall(TLMethod<T> method, int timeout, int destDc) throws IOException {
        return doRpcCall(method, timeout, destDc, true);
    }

    private <T extends TLObject> T doRpcCall(TLMethod<T> method, int timeout, int destDc, boolean authRequired) throws IOException {
        if (isClosed) {
            throw new TimeoutException();
        }
        final Object waitObj = new Object();
        final Object[] res = new Object[3];
        final boolean[] completed = new boolean[1];
        completed[0] = false;

        doRpcCall(method, timeout, new RpcCallback<T>() {
            @Override
            public void onResult(T result) {
                synchronized (waitObj) {
                    if (completed[0]) {
                        return;
                    }
                    completed[0] = true;
                    res[0] = result;
                    res[1] = null;
                    res[2] = null;
                    waitObj.notifyAll();
                }
            }

            @Override
            public void onError(int errorCode, String message) {
                synchronized (waitObj) {
                    if (completed[0]) {
                        return;
                    }
                    completed[0] = true;
                    res[0] = null;
                    res[1] = errorCode;
                    res[2] = message;
                    waitObj.notifyAll();
                }
            }
        }, destDc, authRequired);

        synchronized (waitObj) {
            try {
                waitObj.wait(timeout);
                completed[0] = true;
            } catch (InterruptedException e) {
                throw new TimeoutException();
            }
        }

        if (res[0] == null) {
            if (res[1] != null) {
                Integer code = (Integer) res[1];
                if (code == 0) {
                    throw new TimeoutException();
                } else {
                    throw new RpcException(code, (String) res[2]);
                }
            } else {
                throw new TimeoutException();
            }
        } else {
            return (T) res[0];
        }
    }

    // Public async methods
    public <T extends TLObject> void doRpcCallWeak(TLMethod<T> method) {
        doRpcCallWeak(method, DEFAULT_TIMEOUT);
    }

    public <T extends TLObject> void doRpcCallWeak(TLMethod<T> method, int timeout) {
        doRpcCall(method, timeout, (RpcCallback) null);
    }

    public <T extends TLObject> void doRpcCall(TLMethod<T> method, RpcCallback<T> callback) {
        doRpcCall(method, DEFAULT_TIMEOUT, callback);
    }

    public <T extends TLObject> void doRpcCall(TLMethod<T> method, int timeout, RpcCallback<T> callback) {
        doRpcCall(method, timeout, callback, 0);
    }

    // Public sync methods

    public <T extends TLObject> T doRpcCall(TLMethod<T> method) throws IOException {
        return doRpcCall(method, DEFAULT_TIMEOUT);
    }

    public <T extends TLObject> T doRpcCall(TLMethod<T> method, int timeout) throws IOException {
        return doRpcCall(method, timeout, 0);
    }

    public <T extends TLObject> T doRpcCallSide(TLMethod<T> method) throws IOException {
        return doRpcCall(method, DEFAULT_TIMEOUT, primaryDc, true);
    }

    public <T extends TLObject> T doRpcCallSide(TLMethod<T> method, int timeout) throws IOException {
        return doRpcCall(method, timeout, primaryDc, true);
    }

    public <T extends TLObject> T doRpcCallSideGzip(TLMethod<T> method, int timeout) throws IOException {
        return doRpcCall(new GzipRequest<T>(method), timeout, primaryDc, true);
    }

    public <T extends TLObject> T doRpcCallGzip(TLMethod<T> method, int timeout) throws IOException {
        return doRpcCall(new GzipRequest<T>(method), timeout, 0);
    }

    public <T extends TLObject> T doRpcCallNonAuth(TLMethod<T> method) throws IOException {
        return doRpcCallNonAuth(method, DEFAULT_TIMEOUT, primaryDc);
    }

    public <T extends TLObject> T doRpcCallNonAuth(TLMethod<T> method, int dcId) throws IOException {
        return doRpcCallNonAuth(method, DEFAULT_TIMEOUT, dcId);
    }

    public <T extends TLObject> T doRpcCallNonAuth(TLMethod<T> method, int timeout, int dcId) throws IOException {
        return doRpcCall(method, timeout, dcId, false);
    }

    public <T extends TLObject> void doRpcCallNonAuth(TLMethod<T> method, int timeout, RpcCallback<T> callback) {
        doRpcCall(method, timeout, callback, 0, false);
    }

    public boolean doSaveFilePart(long _fileId, int _filePart, byte[] _bytes) throws IOException {
        TLBool res = doRpcCall(
                new TLRequestUploadSaveFilePart(_fileId, _filePart, _bytes),
                FILE_TIMEOUT,
                primaryDc,
                true);
        return res instanceof TLBoolTrue;
    }

    public boolean doSaveBigFilePart(long _fileId, int _filePart, int _totalParts, byte[] _bytes) throws IOException {
        TLBool res = doRpcCall(
                new TLRequestUploadSaveBigFilePart(_fileId, _filePart, _totalParts, _bytes),
                FILE_TIMEOUT,
                primaryDc);
        return res instanceof TLBoolTrue;
    }

    public TLFile doGetFile(int dcId, org.telegram.api.TLAbsInputFileLocation _location, int _offset, int _limit) throws IOException {
        return doRpcCall(new TLRequestUploadGetFile(_location, _offset, _limit), FILE_TIMEOUT, dcId);
    }

    private void checkDcAuth(int dcId) {
        if (dcId != 0) {
            synchronized (dcProtos) {
                if (!dcProtos.containsKey(dcId)) {
                    synchronized (dcRequired) {
                        dcRequired.put(dcId, true);
                        dcRequired.notifyAll();
                    }
                } else if (!state.isAuthenticated(dcId)) {
                    synchronized (dcRequired) {
                        dcRequired.put(dcId, true);
                        dcRequired.notifyAll();
                    }
                }
            }
        }
    }

    private void checkDc(int dcId) {
        if (dcId != 0) {
            synchronized (dcProtos) {
                if (!dcProtos.containsKey(dcId)) {
                    synchronized (dcRequired) {
                        if (!dcRequired.containsKey(dcId)) {
                            dcRequired.put(dcId, false);
                        }
                        dcRequired.notifyAll();
                    }
                }
            }
        } else if (mainProto == null) {
            synchronized (dcRequired) {
                dcRequired.notifyAll();
            }
        }
    }

    private class ProtoCallback implements MTProtoCallback {

        @Override
        public void onSessionCreated(MTProto proto) {
            if (isClosed) {
                return;
            }

            Logger.w(TAG, proto + ": onSessionCreated");

            if (proto == mainProto) {
                registeredInApi.add(primaryDc);
            } else {
                for (Map.Entry<Integer, MTProto> p : dcProtos.entrySet()) {
                    if (p.getValue() == proto) {
                        registeredInApi.add(p.getKey());
                        break;
                    }
                }
            }

            apiCallback.onUpdatesInvalidated(TelegramApi.this);
        }

        @Override
        public void onAuthInvalidated(MTProto proto) {
            if (isClosed) {
                return;
            }

            if (proto == mainProto) {
                synchronized (dcRequired) {
                    mainProto.close();
                    mainProto = null;
                    state.setAuthenticated(primaryDc, false);
                    dcRequired.notifyAll();
                }

                synchronized (dcProtos) {
                    for (Map.Entry<Integer, MTProto> p : dcProtos.entrySet()) {
                        p.getValue().close();
                        state.setAuthenticated(p.getKey(), false);
                    }
                }

                apiCallback.onAuthCancelled(TelegramApi.this);
            } else {
                synchronized (dcProtos) {
                    for (Map.Entry<Integer, MTProto> p : dcProtos.entrySet()) {
                        if (p.getValue() == proto) {
                            state.setAuthenticated(p.getKey(), false);
                            dcProtos.remove(p.getKey());
                            break;
                        }
                    }
                }
                synchronized (dcRequired) {
                    dcRequired.notifyAll();
                }
            }
        }

        @Override
        public void onApiMessage(byte[] message, MTProto proto) {
            if (isClosed) {
                return;
            }

            if (proto == mainProto) {
                registeredInApi.add(primaryDc);
            } else {
                for (Map.Entry<Integer, MTProto> p : dcProtos.entrySet()) {
                    if (p.getValue() == proto) {
                        registeredInApi.add(p.getKey());
                        break;
                    }
                }
            }

            try {
                TLObject object = apiContext.deserializeMessage(message);
                onMessageArrived(object);
            } catch (Throwable t) {
                Logger.e(TAG, t);
            }
        }

        @Override
        public void onRpcResult(int callId, byte[] response, MTProto proto) {
            if (isClosed) {
                return;
            }

            if (proto == mainProto) {
                registeredInApi.add(primaryDc);
            } else {
                for (Map.Entry<Integer, MTProto> p : dcProtos.entrySet()) {
                    if (p.getValue() == proto) {
                        registeredInApi.add(p.getKey());
                        break;
                    }
                }
            }

            try {
                RpcCallbackWrapper currentCallback = null;
                synchronized (callbacks) {
                    if (sentRequests.containsKey(callId)) {
                        currentCallback = callbacks.remove(sentRequests.remove(callId));
                    }
                }
                if (currentCallback != null && currentCallback.method != null) {
                    long start = System.currentTimeMillis();
                    TLObject object = currentCallback.method.deserializeResponse(response, apiContext);
                    Logger.d(TAG, "<< #" + +currentCallback.id + " deserialized " + object + " in " + (System.currentTimeMillis() - start) + " ms");

                    synchronized (currentCallback) {
                        if (currentCallback.isCompleted) {
                            Logger.d(TAG, "<< #" + +currentCallback.id + " ignored " + object + " in " + currentCallback.elapsed() + " ms");
                            return;
                        } else {
                            currentCallback.isCompleted = true;
                        }
                    }
                    Logger.d(TAG, "<< #" + +currentCallback.id + " " + object + " in " + currentCallback.elapsed() + " ms");

                    synchronized (timeoutTimes) {
                        timeoutTimes.remove(currentCallback.timeoutTime);
                    }
                    if (currentCallback.callback != null) {
                        currentCallback.callback.onResult(object);
                    }
                }
            } catch (Throwable t) {
                Logger.e(TAG, t);
            }
        }

        @Override
        public void onRpcError(int callId, int errorCode, String message, MTProto proto) {
            if (isClosed) {
                return;
            }

            if (errorCode == 400 && message != null &&
                    (message.startsWith("CONNECTION_NOT_INITED") || message.startsWith("CONNECTION_LAYER_INVALID"))) {
                Logger.w(TAG, proto + ": (!)Error #400 " + message);

                int dc = -1;
                if (proto == mainProto) {
                    dc = primaryDc;
                } else {
                    for (Map.Entry<Integer, MTProto> p : dcProtos.entrySet()) {
                        if (p.getValue() == proto) {
                            dc = p.getKey();
                            break;
                        }
                    }
                }
                if (dc < 0) {
                    return;
                }
                registeredInApi.remove(dc);

                RpcCallbackWrapper currentCallback;
                synchronized (callbacks) {
                    currentCallback = callbacks.remove(sentRequests.remove(callId));
                    if (currentCallback != null) {
                        currentCallback.isSent = false;
                        callbacks.notifyAll();
                    }
                }

                return;
            } else {
                if (proto == mainProto) {
                    registeredInApi.add(primaryDc);
                } else {
                    for (Map.Entry<Integer, MTProto> p : dcProtos.entrySet()) {
                        if (p.getValue() == proto) {
                            registeredInApi.add(p.getKey());
                            break;
                        }
                    }
                }
            }

            try {
                RpcCallbackWrapper currentCallback = null;
                synchronized (callbacks) {
                    if (sentRequests.containsKey(callId)) {
                        currentCallback = callbacks.remove(sentRequests.remove(callId));
                    }
                }
                if (currentCallback != null) {
                    synchronized (currentCallback) {
                        if (currentCallback.isCompleted) {
                            Logger.d(TAG, "<< #" + +currentCallback.id + " ignored error #" + errorCode + " " + message + " in " + currentCallback.elapsed() + " ms");
                            return;
                        } else {
                            currentCallback.isCompleted = true;
                        }
                    }
                    Logger.d(TAG, "<< #" + +currentCallback.id + " error #" + errorCode + " " + message + " in " + currentCallback.elapsed() + " ms");
                    synchronized (timeoutTimes) {
                        timeoutTimes.remove(currentCallback.timeoutTime);
                    }
                    if (currentCallback.callback != null) {
                        currentCallback.callback.onError(errorCode, message);
                    }
                }
            } catch (Throwable t) {
                Logger.e(TAG, t);
            }
        }

        @Override
        public void onConfirmed(int callId) {
            RpcCallbackWrapper currentCallback = null;
            synchronized (callbacks) {
                if (sentRequests.containsKey(callId)) {
                    currentCallback = callbacks.get(sentRequests.get(callId));
                }
            }
            if (currentCallback != null) {
                Logger.d(TAG, "<< #" + +currentCallback.id + " confirmed in " + currentCallback.elapsed() + " ms");
                synchronized (currentCallback) {
                    if (currentCallback.isCompleted || currentCallback.isConfirmed) {
                        return;
                    } else {
                        currentCallback.isConfirmed = true;
                    }
                }
                if (currentCallback.callback instanceof RpcCallbackEx) {
                    ((RpcCallbackEx) currentCallback.callback).onConfirmed();
                }
            }
        }
    }

    private class SenderThread extends Thread {
        public SenderThread() {
            setName("Sender#" + hashCode());
        }

        @Override
        public void run() {
            setPriority(Thread.MIN_PRIORITY);
            while (!isClosed) {
                Logger.d(TAG, "Sender iteration");
                RpcCallbackWrapper wrapper = null;
                synchronized (callbacks) {
                    for (RpcCallbackWrapper w : callbacks.values()) {
                        if (!w.isSent) {
                            if (w.dcId == 0 && mainProto != null) {
                                if (state.isAuthenticated(primaryDc) || !w.isAuthRequred) {
                                    wrapper = w;
                                    break;
                                }
                            }
                            if (w.dcId != 0 && dcProtos.containsKey(w.dcId)) {
                                if (state.isAuthenticated(w.dcId) || !w.isAuthRequred) {
                                    wrapper = w;
                                    break;
                                }
                            }
                        }
                    }
                    if (wrapper == null) {
                        try {
                            callbacks.wait();
                        } catch (InterruptedException e) {
                            Logger.e(TAG, e);
                            return;
                        }
                        continue;
                    }
                }

                if (mainProto == null) {
                    continue;
                }

                if (wrapper.dcId == 0) {
                    if (!state.isAuthenticated(primaryDc) && wrapper.isAuthRequred) {
                        continue;
                    }
                    synchronized (callbacks) {
                        boolean isHighPriority = wrapper.callback != null && wrapper.callback instanceof RpcCallbackEx;
                        int rpcId = mainProto.sendRpcMessage(wrapper.method, wrapper.timeout, isHighPriority);
                        sentRequests.put(rpcId, wrapper.id);
                        wrapper.isSent = true;
                        Logger.d(TAG, "#> #" + wrapper.id + " sent to MTProto #" + mainProto.getInstanceIndex() + " with id #" + rpcId);
                    }
                } else {
                    if (!dcProtos.containsKey(wrapper.dcId) || (!state.isAuthenticated(wrapper.dcId) && wrapper.isAuthRequred)) {
                        continue;
                    }

                    MTProto proto = dcProtos.get(wrapper.dcId);
                    synchronized (callbacks) {
                        boolean isHighPriority = wrapper.callback != null && wrapper.callback instanceof RpcCallbackEx;
                        int rpcId = proto.sendRpcMessage(wrapper.method, wrapper.timeout, isHighPriority);
                        sentRequests.put(rpcId, wrapper.id);
                        wrapper.isSent = true;
                        Logger.d(TAG, "#> #" + wrapper.id + " sent to MTProto #" + proto.getInstanceIndex() + " with id #" + rpcId);
                    }
                }
            }
        }
    }

    private class ConnectionThread extends Thread {
        public ConnectionThread() {
            setName("Connection#" + hashCode());
        }

        private MTProto waitForDc(final int dcId) throws IOException {
            Logger.d(TAG, "#" + dcId + ": waitForDc");
            if (isClosed) {
                Logger.w(TAG, "#" + dcId + ": Api is closed");
                throw new TimeoutException();
            }

//        if (!state.isAuthenticated(primaryDc)) {
//            Logger.w(TAG, "#" + dcId + ": Dc is not authenticated");
//            throw new TimeoutException();
//        }

            Object syncObj;
            synchronized (dcSync) {
                syncObj = dcSync.get(dcId);
                if (syncObj == null) {
                    syncObj = new Object();
                    dcSync.put(dcId, syncObj);
                }
            }

            synchronized (syncObj) {
                MTProto proto;
                synchronized (dcProtos) {
                    proto = dcProtos.get(dcId);
                    if (proto != null) {
                        if (proto.isClosed()) {
                            Logger.d(TAG, "#" + dcId + "proto removed because of death");
                            dcProtos.remove(dcId);
                            proto = null;
                        }
                    }
                }

                if (proto == null) {
                    Logger.d(TAG, "#" + dcId + ": Creating proto for dc");
                    ConnectionInfo[] connectionInfo = state.getAvailableConnections(dcId);

                    if (connectionInfo.length == 0) {
                        Logger.w(TAG, "#" + dcId + ": Unable to find proper dc config");
                        TLConfig config = doRpcCall(new TLRequestHelpGetConfig());
                        state.updateSettings(config);
                        resetConnectionInfo();
                        connectionInfo = state.getAvailableConnections(dcId);
                    }

                    if (connectionInfo.length == 0) {
                        Logger.w(TAG, "#" + dcId + ": Still unable to find proper dc config");
                        throw new TimeoutException();
                    }

                    if (state.getAuthKey(dcId) != null) {
                        byte[] authKey = state.getAuthKey(dcId);
                        if (authKey == null) {
                            throw new TimeoutException();
                        }
                        proto = new MTProto(state.getMtProtoState(dcId), callback,
                                new CallWrapper() {
                                    @Override
                                    public TLObject wrapObject(TLMethod srcRequest) {
                                        return wrapForDc(dcId, srcRequest);
                                    }
                                }, CHANNELS_FS, MTProto.MODE_GENERAL);

                        dcProtos.put(dcId, proto);
                        return proto;
                    } else {
                        Logger.w(TAG, "#" + dcId + ": Creating key");
                        Authorizer authorizer = new Authorizer();
                        PqAuth auth = authorizer.doAuth(connectionInfo);
                        if (auth == null) {
                            Logger.w(TAG, "#" + dcId + ": Timed out");
                            throw new TimeoutException();
                        }
                        state.putAuthKey(dcId, auth.getAuthKey());
                        state.setAuthenticated(dcId, false);
                        state.getMtProtoState(dcId).initialServerSalt(auth.getServerSalt());

                        byte[] authKey = state.getAuthKey(dcId);
                        if (authKey == null) {
                            Logger.w(TAG, "#" + dcId + ": auth key == null");
                            throw new TimeoutException();
                        }

                        proto = new MTProto(state.getMtProtoState(dcId), callback,
                                new CallWrapper() {
                                    @Override
                                    public TLObject wrapObject(TLMethod srcRequest) {
                                        return wrapForDc(dcId, srcRequest);
                                    }
                                }, CHANNELS_FS, MTProto.MODE_GENERAL);

                        dcProtos.put(dcId, proto);

                        return proto;
                    }
                } else {
                    Logger.w(TAG, "#" + dcId + ": returning proper proto");
                    return proto;
                }
            }
        }

        private MTProto waitForAuthDc(final int dcId) throws IOException {
            Logger.d(TAG, "#" + dcId + ": waitForAuthDc");
            if (isClosed) {
                Logger.w(TAG, "#" + dcId + ": Api is closed");
                throw new TimeoutException();
            }

            MTProto proto = waitForDc(dcId);

            if (!state.isAuthenticated(dcId)) {
                Logger.w(TAG, "#" + dcId + ": exporting auth");
                TLExportedAuthorization exAuth = doRpcCall(new TLRequestAuthExportAuthorization(dcId));

                Logger.w(TAG, "#" + dcId + ": importing auth");
                doRpcCallNonAuth(new TLRequestAuthImportAuthorization(exAuth.getId(), exAuth.getBytes()), DEFAULT_TIMEOUT, dcId);

                state.setAuthenticated(dcId, true);
            }

            return proto;
        }

        @Override
        public void run() {
            setPriority(Thread.MIN_PRIORITY);
            while (!isClosed) {
                Logger.d(TAG, "Connection iteration");
                if (mainProto == null) {
                    if (state.getAuthKey(primaryDc) == null) {
                        try {
                            long start = System.currentTimeMillis();
                            waitForDc(primaryDc);
                            mainProto = new MTProto(state.getMtProtoState(primaryDc), callback,
                                    new CallWrapper() {
                                        @Override
                                        public TLObject wrapObject(TLMethod srcRequest) {
                                            return wrapForDc(primaryDc, srcRequest);
                                        }
                                    }, CHANNELS_MAIN, MTProto.MODE_GENERAL);
                            Logger.d(TAG, "#MTProto #" + mainProto.getInstanceIndex() + " created in " + (System.currentTimeMillis() - start) + " ms");
                        } catch (IOException e) {
                            Logger.e(TAG, e);
                            try {
                                Thread.sleep(1000);
                                continue;
                            } catch (InterruptedException e1) {
                                Logger.e(TAG, e1);
                                return;
                            }
                        }
                    } else {
                        long start = System.currentTimeMillis();
                        mainProto = new MTProto(state.getMtProtoState(primaryDc), callback,
                                new CallWrapper() {
                                    @Override
                                    public TLObject wrapObject(TLMethod srcRequest) {
                                        return wrapForDc(primaryDc, srcRequest);
                                    }
                                }, CHANNELS_MAIN, MTProto.MODE_GENERAL);
                        Logger.d(TAG, "#MTProto #" + mainProto.getInstanceIndex() + " created in " + (System.currentTimeMillis() - start) + " ms");
                    }
                    synchronized (callbacks) {
                        callbacks.notifyAll();
                    }
                    continue;
                }

                Integer dcId = null;
                Boolean authRequired = null;
                synchronized (dcRequired) {
                    if (dcRequired.isEmpty()) {
                        dcId = null;
                        authRequired = null;
                    } else {
                        try {
                            dcId = dcRequired.firstKey();
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                        }
                    }

                    if (dcId == null) {
                        try {
                            dcRequired.wait();
                        } catch (InterruptedException e) {
                            // e.printStackTrace();
                        }
                        continue;
                    }

                    authRequired = dcRequired.remove(dcId);
                }

                if (dcProtos.containsKey(dcId)) {
                    if (authRequired && !state.isAuthenticated(dcId) && state.isAuthenticated(primaryDc)) {
                        try {
                            waitForAuthDc(dcId);
                            synchronized (callbacks) {
                                callbacks.notifyAll();
                            }
                        } catch (IOException e) {
                            try {
                                Thread.sleep(1000);
                                continue;
                            } catch (InterruptedException e1) {
                                Logger.e(TAG, e1);
                                return;
                            }
                        }
                    }
                } else {
                    try {
                        if (authRequired && !state.isAuthenticated(dcId) && state.isAuthenticated(primaryDc)) {
                            waitForAuthDc(dcId);
                        } else {
                            waitForDc(dcId);
                        }
                        synchronized (callbacks) {
                            callbacks.notifyAll();
                        }
                    } catch (IOException e) {
                        Logger.e(TAG, e);
                    }
                }
            }
        }
    }

    private class TimeoutThread extends Thread {
        public TimeoutThread() {
            setName("Timeout#" + hashCode());
        }

        @Override
        public void run() {
            while (!isClosed) {
                Logger.d(TAG, "Timeout Iteration");
                Long key = null;
                Integer id = null;
                synchronized (timeoutTimes) {
                    if (timeoutTimes.isEmpty()) {
                        key = null;
                    } else {
                        try {
                            key = timeoutTimes.firstKey();
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                        }
                    }

                    if (key == null) {
                        try {
                            timeoutTimes.wait();
                        } catch (InterruptedException e) {
                            // e.printStackTrace();
                        }
                        continue;
                    }

                    long delta = (key - System.nanoTime()) / (1000 * 1000);
                    if (delta > 0) {
                        try {
                            timeoutTimes.wait(delta);
                        } catch (InterruptedException e) {
                            // e.printStackTrace();
                        }
                        continue;
                    }

                    id = timeoutTimes.remove(key);
                    if (id == null) {
                        continue;
                    }
                }

                RpcCallbackWrapper currentCallback;
                synchronized (callbacks) {
                    currentCallback = callbacks.remove(id);
                }
                if (currentCallback != null) {
                    synchronized (currentCallback) {
                        if (currentCallback.isCompleted) {
                            Logger.d(TAG, "RPC #" + id + ": Timeout ignored");
                            return;
                        } else {
                            currentCallback.isCompleted = true;
                        }
                    }
                    Logger.d(TAG, "RPC #" + id + ": Timeout (" + currentCallback.elapsed() + " ms)");
                    currentCallback.callback.onError(0, null);
                } else {
                    Logger.d(TAG, "RPC #" + id + ": Timeout ignored2");
                }
            }
            synchronized (timeoutTimes) {
                for (Map.Entry<Long, Integer> entry : timeoutTimes.entrySet()) {
                    RpcCallbackWrapper currentCallback;
                    synchronized (callbacks) {
                        currentCallback = callbacks.remove(entry.getValue());
                    }
                    if (currentCallback != null) {
                        synchronized (currentCallback) {
                            if (currentCallback.isCompleted) {
                                return;
                            } else {
                                currentCallback.isCompleted = true;
                            }
                        }
                        Logger.d(TAG, "RPC #" + entry.getValue() + ": Timeout (" + currentCallback.elapsed() + " ms)");
                        currentCallback.callback.onError(0, null);
                    }
                }
            }
        }
    }

    private class RpcCallbackWrapper {
        public int id;
        public long requestTime = System.currentTimeMillis();
        public boolean isSent = false;
        public boolean isCompleted = false;
        public boolean isConfirmed = false;
        public RpcCallback callback;
        public long timeoutTime;
        public long timeout;
        public TLMethod method;

        public boolean isAuthRequred;
        public int dcId;

        private RpcCallbackWrapper(int id, TLMethod method, RpcCallback callback) {
            this.id = id;
            this.method = method;
            this.callback = callback;
        }

        public long elapsed() {
            return System.currentTimeMillis() - requestTime;
        }
    }
}
