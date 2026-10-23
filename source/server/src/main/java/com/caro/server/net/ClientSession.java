package com.caro.server.net;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.caro.server.auth.AuthenticatedUser;

/**
 * One connected client, independent of the transport. Subclasses: {@code TcpClientSession}, {@code WsClientSession}.
 *
 * <p>Outgoing messages go through a bounded queue drained by a dedicated writer thread, so:
 * <ul>
 *   <li>{@link #send} never blocks: game logic can call it while holding a lock;</li>
 *   <li>only one thread ever writes to the connection, so messages are never interleaved;</li>
 *   <li>a client that stops reading (full queue) is disconnected instead of exhausting server memory.</li>
 * </ul>
 */
public abstract class ClientSession {

    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);
    private static final AtomicLong SEQ = new AtomicLong();
    /** Queue marker meaning "stop writing and close". Compared by identity, hence the explicit new String. */
    @SuppressWarnings("StringOperationCanBeSimplified")
    private static final String CLOSE_SIGNAL = new String("<close>");

    private final String id;
    private final String remoteAddress;
    private final BlockingQueue<String> outbound;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean released = new AtomicBoolean();
    private volatile long lastSeenAt = System.currentTimeMillis();
    private volatile AuthenticatedUser user;

    protected ClientSession(String transport, String remoteAddress, int maxOutboundQueue) {
        this.id = transport + "-" + SEQ.incrementAndGet();
        this.remoteAddress = remoteAddress;
        this.outbound = new LinkedBlockingQueue<>(maxOutboundQueue);
    }

    /** Starts the writer thread. Called by the transport once the connection is ready. */
    public void start() {
        Thread.ofVirtual().name("writer-" + id).start(this::writeLoop);
    }

    /** Queues a JSON message. Never blocks. */
    public void send(String json) {
        if (closing.get()) {
            return;
        }
        if (!outbound.offer(json)) {
            log.warn("{}: outbound queue full (client too slow), closing", id);
            close();
        }
    }

    /** Sends everything already queued, then closes the connection. */
    public void closeAfterFlush() {
        if (closing.compareAndSet(false, true) && !outbound.offer(CLOSE_SIGNAL)) {
            closeTransport();
        }
    }

    /** Closes the connection now, dropping queued messages. */
    public void close() {
        closing.set(true);
        outbound.clear();
        outbound.offer(CLOSE_SIGNAL); // wakes the writer thread up so it can exit
        closeTransport();
    }

    private void writeLoop() {
        try {
            while (true) {
                String message = outbound.take();
                if (message == CLOSE_SIGNAL) {
                    break;
                }
                write(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            log.debug("{}: write failed: {}", id, e.toString());
        } finally {
            closing.set(true);
            closeTransport();
        }
    }

    /** Writes one message to the connection. Only ever called from the writer thread. */
    protected abstract void write(String json) throws IOException;

    /** Closes the underlying connection. Must be idempotent. */
    protected abstract void closeTransport();

    /** @return true only on the first call; makes disconnect handling run exactly once */
    boolean markReleased() {
        return released.compareAndSet(false, true);
    }

    public String id() {
        return id;
    }

    public String remoteAddress() {
        return remoteAddress;
    }

    public void touch() {
        lastSeenAt = System.currentTimeMillis();
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }

    /** @return the logged-in user, or null before LOGIN/AUTH */
    public AuthenticatedUser user() {
        return user;
    }

    void setUser(AuthenticatedUser user) {
        this.user = user;
    }
}
