package org.telegram.mtproto.transport;

import com.droidkit.actors.*;
import org.telegram.mtproto.MTProto;
import org.telegram.mtproto.backoff.ExponentalBackoff;
import org.telegram.mtproto.log.Logger;
import org.telegram.mtproto.schedule.PrepareSchedule;
import org.telegram.mtproto.schedule.PreparedPackage;
import org.telegram.mtproto.schedule.Scheduller;
import org.telegram.mtproto.secure.Entropy;
import org.telegram.mtproto.tl.MTMessage;
import org.telegram.mtproto.tl.MTPing;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import static org.telegram.mtproto.util.TimeUtil.getUnixTime;

/**
 * Created by ex3ndr on 03.04.14.
 */
public class TransportTcpPool extends TransportPool {

    private static final boolean LOG_SCHEDULLER = true;

    private final String TAG;
    private static final boolean USE_CHECKSUM = false;
    private static final int LOW_TIME_DIE_CHECK = 30 * 1000; // 30 sec

    private int desiredConnectionCount;

    private final HashSet<TcpContext> contexts = new HashSet<TcpContext>();
    private final HashMap<Integer, Integer> contextConnectionId = new HashMap<Integer, Integer>();
    private final HashSet<Integer> connectedContexts = new HashSet<Integer>();
    private final HashSet<Integer> initedContext = new HashSet<Integer>();

    private static final int PING_TIMEOUT = 60 * 1000;

    private ActorSystem actorSystem;

    private TransportRate connectionRate;

    private TcpListener tcpListener;

    private ActorRef connectionActor;
    private ActorRef scheduleActor;
//    private ConnectionActor.ConnectorMessenger connectionActor;
//    private SchedullerActor.Messenger scheduleActor;

    private ExponentalBackoff exponentalBackoff;

    public TransportTcpPool(MTProto proto, TransportPoolCallback callback, int connectionCount) {
        super(proto, callback);
        TAG = "TransportTcpPool#" + proto.getInstanceIndex();
        this.exponentalBackoff = new ExponentalBackoff(TAG);
        this.desiredConnectionCount = connectionCount;
        this.actorSystem = proto.getActorSystem();
        this.tcpListener = new TcpListener();
        this.connectionRate = new TransportRate(proto.getState().getAvailableConnections());

        connectionActor = actorSystem.actorOf(connection());
        scheduleActor = actorSystem.actorOf(scheduller());
    }

    @Override
    public void onSchedullerUpdated(Scheduller scheduller) {
        if (LOG_SCHEDULLER) {
            Logger.d(TAG, "onSchedullerUpdated");
        }
        scheduleActor.send(new Schedule());
        synchronized (contexts) {
            if (contexts.size() == 0) {
                this.connectionActor.send(new CheckConnections());
            }
        }
        this.connectionActor.sendOnce(new CheckDestroy(), LOW_TIME_DIE_CHECK);
    }

    @Override
    public void reloadConnectionInformation() {
        this.connectionRate = new TransportRate(proto.getState().getAvailableConnections());
    }

    @Override
    public void resetConnectionBackoff() {
        exponentalBackoff.reset();
    }

    @Override
    protected void onModeChanged() {
        this.scheduleActor.send(new Schedule());
        this.connectionActor.send(new CheckConnections());
        this.connectionActor.sendOnce(new CheckDestroy(), LOW_TIME_DIE_CHECK);
    }

    private ActorSelection connection() {
        return new ActorSelection(Props.create(ConnectionActor.class, new ActorCreator<ConnectionActor>() {
            @Override
            public ConnectionActor create() {
                return new ConnectionActor(TransportTcpPool.this);
            }
        }).changeDispatcher("connection"),
                "tcp_connection_" + proto.getInstanceIndex());
    }

    private ActorSelection scheduller() {
        return new ActorSelection(Props.create(SchedullerActor.class, new ActorCreator<SchedullerActor>() {
            @Override
            public SchedullerActor create() {
                return new SchedullerActor(TransportTcpPool.this);
            }
        }), "tcp_scheduller_" + proto.getInstanceIndex());
    }

    private static final class CheckDestroy {

    }

    private static final class CheckConnections {

    }

    private static final class Schedule {

    }

    private static class ConnectionActor extends Actor {
        private TransportTcpPool pool;

        private ConnectionActor(TransportTcpPool pool) {
            this.pool = pool;
        }

        @Override
        public void preStart() {
            self().send(new CheckConnections());
        }

        @Override
        public void onReceive(Object message) {
            if (message instanceof CheckDestroy) {
                onCheckDestroyMessage();
            } else if (message instanceof CheckConnections) {
                onCheckMessage();
            }
        }

        protected void onCheckDestroyMessage() {
            if (pool.mode == MODE_LOWMODE) {
                if (pool.scheduller.hasRequests()) {
                    self().send(new CheckDestroy(), LOW_TIME_DIE_CHECK);
                    return;
                }

                // Logger.d(TAG, "Destroying contexts");
                synchronized (pool.contexts) {
                    for (TcpContext c : pool.contexts) {
                        c.close();
                    }
                    pool.contexts.clear();
                }
            }
        }

        protected void onCheckMessage() {
            try {
                if (pool.mode == MODE_LOWMODE) {
                    if (!pool.scheduller.hasRequests()) {
                        // Logger.d(TAG, "Ignoring context check: scheduller is empty in low mode.");
                        return;
                    }
                }

                synchronized (pool.contexts) {
                    if (pool.contexts.size() >= pool.desiredConnectionCount) {
                        // Logger.d(TAG, "Ignoring context check: already created enough contexts.");
                        return;
                    }
                }

                ConnectionType type = pool.connectionRate.tryConnection();
                // Logger.d(TAG, "Creating context for #" + type.getId() + " " + type.getHost() + ":" + type.getPort());
                try {
                    TcpContext context = new TcpContext(pool.proto, type.getHost(), type.getPort(), USE_CHECKSUM, pool.tcpListener);
                    // Logger.d(TAG, "Context created.");
                    synchronized (pool.contexts) {
                        pool.contexts.add(context);
                        pool.contextConnectionId.put(context.getContextId(), type.getId());
                    }
                    pool.scheduller.postMessageDelayed(new MTPing(Entropy.generateRandomId()), false, PING_TIMEOUT, 0, context.getContextId(), false);
                } catch (IOException e) {
                    // Logger.d(TAG, "Context create failure.");
                    pool.connectionRate.onConnectionFailure(type.getId());
                    throw e;
                }

                // messenger().check();
                self().send(new CheckConnections());
            } catch (Exception e) {
                self().send(new CheckConnections(), 1000);
            }
        }
    }

    private static class SchedullerActor extends Actor {
        private TransportTcpPool pool;
        private PrepareSchedule prepareSchedule = new PrepareSchedule();
        private int roundRobin = 0;

        private SchedullerActor(TransportTcpPool pool) {
            this.pool = pool;
        }

        @Override
        public void preStart() {
            self().send(new Schedule());
        }

        @Override
        public void onReceive(Object message) {
            if (message instanceof Schedule) {
                onScheduleMessage();
            }
        }

        public void onScheduleMessage() {
            // Logger.d(TAG, "onScheduleMessage");
            int[] contextIds;
            synchronized (pool.contexts) {
                TcpContext[] currentContexts = pool.contexts.toArray(new TcpContext[0]);
                contextIds = new int[currentContexts.length];
                for (int i = 0; i < contextIds.length; i++) {
                    contextIds[i] = currentContexts[i].getContextId();
                }
            }

            pool.scheduller.prepareScheduller(prepareSchedule, contextIds);
            if (prepareSchedule.isDoWait()) {
//                if (LOG_SCHEDULLER) {
//                    Logger.d(TAG, "Scheduller:wait " + prepareSchedule.getDelay());
//                }
                self().sendOnce(new Schedule(), prepareSchedule.getDelay());
                return;
            }

            TcpContext context = null;
            synchronized (pool.contexts) {
                TcpContext[] currentContexts = pool.contexts.toArray(new TcpContext[0]);
                outer:
                for (int i = 0; i < currentContexts.length; i++) {
                    int index = (i + roundRobin + 1) % currentContexts.length;
                    for (int allowed : prepareSchedule.getAllowedContexts()) {
                        if (currentContexts[index].getContextId() == allowed) {
                            context = currentContexts[index];
                            break outer;
                        }
                    }

                }

                if (currentContexts.length != 0) {
                    roundRobin = (roundRobin + 1) % currentContexts.length;
                }
            }

            if (context == null) {
//                if (LOG_SCHEDULLER) {
//                    Logger.d(TAG, "Scheduller: no context");
//                }
//                messenger().schedule();
                self().sendOnce(new Schedule());
                return;
            }

//            if (LOG_SCHEDULLER) {
//                Logger.d(TAG, "doSchedule");
//            }

            long start = System.currentTimeMillis();
            PreparedPackage preparedPackage = pool.scheduller.doSchedule(context.getContextId(), pool.initedContext.contains(context.getContextId()));
//            if (LOG_SCHEDULLER) {
//                Logger.d(TAG, "Schedulled in " + (System.currentTimeMillis() - start) + " ms");
//            }
            if (preparedPackage == null) {
//                if (LOG_SCHEDULLER) {
//                    Logger.d(TAG, "No packages for scheduling");
//                }
//                messenger().schedule();
                self().sendOnce(new Schedule());
                return;
            }

//            if (LOG_SCHEDULLER) {
//                Logger.d(TAG, "MessagePushed (#" + context.getContextId() + "): time:" + getUnixTime(preparedPackage.getMessageId()));
//                Logger.d(TAG, "MessagePushed (#" + context.getContextId() + "): seqNo:" + preparedPackage.getSeqNo() + ", msgId" + preparedPackage.getMessageId());
//            }

            try {
                EncryptedMessage msg = pool.encrypt(preparedPackage.getSeqNo(), preparedPackage.getMessageId(), preparedPackage.getContent());
                if (preparedPackage.isHighPriority()) {
                    pool.scheduller.registerFastConfirm(preparedPackage.getMessageId(), msg.fastConfirm);
                }
                if (!context.isClosed()) {
                    context.postMessage(msg.data, preparedPackage.isHighPriority());
                    pool.initedContext.add(context.getContextId());
                } else {
                    pool.scheduller.onConnectionDies(context.getContextId());
                }
            } catch (IOException e) {
                // Logger.e(TAG, e);
            }

//            if (LOG_SCHEDULLER) {
//                Logger.d(TAG, "doSchedule end");
//            }

            self().sendOnce(new Schedule());
        }
    }

    private class TcpListener implements TcpContextCallback {

        @Override
        public void onRawMessage(byte[] data, int offset, int len, TcpContext context) {
            if (isClosed) {
                return;
            }
            try {
                MTMessage decrypted = decrypt(data, offset, len);
                if (decrypted == null) {
                    Logger.d(TAG, "message ignored");
                    return;
                }
                if (!connectedContexts.contains(context.getContextId())) {
                    connectedContexts.add(context.getContextId());
                    exponentalBackoff.onSuccess();
                    connectionRate.onConnectionSuccess(contextConnectionId.get(context.getContextId()));
                }

                onMTMessage(decrypted);
            } catch (IOException e) {
                Logger.e(TAG, e);
                synchronized (contexts) {
                    context.close();
                    if (!connectedContexts.contains(context.getContextId())) {
                        exponentalBackoff.onFailureNoWait();
                        connectionRate.onConnectionFailure(contextConnectionId.get(context.getContextId()));
                    }
                    contexts.remove(context);
                    connectionActor.send(new CheckConnections());
                    scheduller.onConnectionDies(context.getContextId());
                }
            }
        }

        @Override
        public void onError(int errorCode, TcpContext context) {
            // Fully maintained at transport level: TcpContext dies
        }

        @Override
        public void onChannelBroken(TcpContext context) {
            if (isClosed) {
                return;
            }
            int contextId = context.getContextId();
            Logger.d(TAG, "onChannelBroken (#" + contextId + ")");
            synchronized (contexts) {
                contexts.remove(context);
                if (!connectedContexts.contains(contextId)) {
                    if (contextConnectionId.containsKey(contextId)) {
                        exponentalBackoff.onFailureNoWait();
                        connectionRate.onConnectionFailure(contextConnectionId.get(contextId));
                    }
                }
                connectionActor.send(new CheckConnections());
            }
            scheduller.onConnectionDies(context.getContextId());
        }

        @Override
        public void onFastConfirm(int hash) {
            if (isClosed) {
                return;
            }
            TransportTcpPool.this.onFastConfirm(hash);
        }
    }
}