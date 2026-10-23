package com.caro.client.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One TCP connection to the game server: one JSON message per line (docs/protocol.md, section 1).
 *
 * <p><b>Threads.</b> A reader thread blocks on the socket. Everything it receives, replies and events alike, is
 * handed to the {@code callbackExecutor} (the Swing event thread in the app) <i>in arrival order</i>. So the
 * futures returned by {@link #request} complete on that executor, and the code using this class never has
 * to deal with locks.
 *
 * <p><b>Requests.</b> Each request gets a fresh {@code reqId}; the OK/ERROR with the same id completes its
 * future. Pushed events (no reqId) go to {@link Listener#onEvent}.
 *
 * <p><b>Heartbeat.</b> A PING is sent every 10 s, otherwise the server closes an idle connection after 30 s.
 * The PONG also gives the server clock, used to show countdowns correctly even if the PC clock is off.
 */
public final class Connection implements AutoCloseable {

    public interface Listener {
        /** A pushed event, e.g. MOVE_APPLIED. Runs on the callback executor. */
        void onEvent(String type, JsonNode data);

        /** Called exactly once, when the connection is gone for any reason. Runs on the callback executor. */
        void onClosed(String reason);
    }

    static final long REQUEST_TIMEOUT_MS = 10_000;
    static final long PING_INTERVAL_MS = 10_000;
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final Socket socket;
    private final BufferedReader in;
    private final Writer out;
    private final Executor callbackExecutor;
    private final Listener listener;
    private final ScheduledExecutorService timers;
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile long clockOffsetMs;

    private Connection(Socket socket, Executor callbackExecutor, Listener listener) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.callbackExecutor = callbackExecutor;
        this.listener = listener;
        this.timers = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "caro-timers");
            t.setDaemon(true);
            return t;
        });
    }

    /** Connects (blocking, so not on the Swing thread) and starts the reader and the heartbeat. */
    public static Connection open(String host, int port, Executor callbackExecutor, Listener listener)
            throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true); // small messages, send them right away
            Connection connection = new Connection(socket, callbackExecutor, listener);
            connection.start();
            return connection;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private void start() {
        Thread reader = new Thread(this::readLoop, "caro-reader");
        reader.setDaemon(true);
        reader.start();
        // first PING right away, so the clock offset is known before the first countdown is shown
        timers.scheduleWithFixedDelay(this::ping, 0, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Sends a request. The future completes with the {@code data} of the OK reply, or fails with a
     * {@link ServerException} (ERROR reply, timeout or lost connection).
     */
    public CompletableFuture<JsonNode> request(String type, Object data) {
        String reqId = "c" + seq.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (closed.get()) {
            future.completeExceptionally(disconnected());
            return future;
        }
        pending.put(reqId, future);
        try {
            write(Json.envelope(type, reqId, data));
        } catch (IOException e) {
            close("Mất kết nối tới server");
            return future; // failed by close()
        }
        timers.schedule(() -> fail(reqId, new ServerException(ServerException.TIMEOUT, "Server không phản hồi")),
                REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return future;
    }

    /** Server time estimated from the last PONG, in epoch milliseconds. */
    public long serverNow() {
        return System.currentTimeMillis() + clockOffsetMs;
    }

    public boolean isOpen() {
        return !closed.get();
    }

    @Override
    public void close() {
        close("Đã đóng kết nối");
    }

    /** Closes the socket; pending requests fail and {@link Listener#onClosed} is called once. */
    public void close(String reason) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        timers.shutdownNow();
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        callbackExecutor.execute(() -> {
            List<CompletableFuture<JsonNode>> waiting = new ArrayList<>(pending.values());
            pending.clear();
            waiting.forEach(f -> f.completeExceptionally(disconnected()));
            listener.onClosed(reason);
        });
    }

    // ------------------------------------------------------------------ internals

    private void readLoop() {
        String reason = "Server đã đóng kết nối";
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode message = Json.parse(line);
                callbackExecutor.execute(() -> dispatch(message));
            }
        } catch (IOException | RuntimeException e) {
            if (!closed.get()) {
                reason = "Mất kết nối tới server";
            }
        }
        close(reason);
    }

    /** Runs on the callback executor. */
    private void dispatch(JsonNode message) {
        String type = message.path("type").asText();
        JsonNode data = message.path("data");
        String reqId = message.hasNonNull("reqId") ? message.get("reqId").asText() : null;
        CompletableFuture<JsonNode> future = reqId == null ? null : pending.remove(reqId);
        if (future == null) {
            listener.onEvent(type, data);
        } else if ("ERROR".equals(type)) {
            future.completeExceptionally(new ServerException(data.path("code").asText(),
                    data.path("message").asText()));
        } else {
            future.complete(data);
        }
    }

    private void ping() {
        long sentAt = System.currentTimeMillis();
        request("PING", Map.of()).thenAccept(pong -> {
            long receivedAt = System.currentTimeMillis();
            // the server read its clock roughly half way through the round trip
            clockOffsetMs = pong.path("serverTime").asLong() - (sentAt + receivedAt) / 2;
        });
    }

    private void fail(String reqId, ServerException error) {
        CompletableFuture<JsonNode> future = pending.remove(reqId);
        if (future != null) {
            callbackExecutor.execute(() -> future.completeExceptionally(error));
        }
    }

    private void write(String json) throws IOException {
        synchronized (out) {
            out.write(json);
            out.write('\n');
            out.flush();
        }
    }

    private static ServerException disconnected() {
        return new ServerException(ServerException.DISCONNECTED, "Mất kết nối tới server");
    }
}
