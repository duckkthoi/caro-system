package com.caro.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Minimal protocol client used by the integration tests, over TCP or WebSocket. */
abstract class TestClient implements AutoCloseable {

    private static final long TIMEOUT_MS = 5000;

    protected final ObjectMapper mapper = new ObjectMapper();
    private final LinkedList<JsonNode> inbox = new LinkedList<>();
    private final AtomicInteger seq = new AtomicInteger();
    private volatile boolean closedByServer;

    static TestClient tcp(int port) throws IOException {
        return new Tcp(port);
    }

    static TestClient ws(String url) throws Exception {
        return new Ws(url);
    }

    abstract void sendRaw(String text);

    /** Sends a request and waits for the OK/ERROR answer carrying the same reqId. */
    JsonNode request(String type, Object data) {
        String reqId = "r" + seq.incrementAndGet();
        try {
            sendRaw(mapper.writeValueAsString(Map.of("type", type, "reqId", reqId, "data", data)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return await(m -> reqId.equals(m.path("reqId").asText()));
    }

    /** Like {@link #request} but asserts success and returns the data. */
    JsonNode ok(String type, Object data) {
        JsonNode reply = request(type, data);
        assertThat(reply.path("type").asText()).as("reply to %s: %s", type, reply).isEqualTo("OK");
        return reply.path("data");
    }

    /** Waits for the next message of this type that was not consumed yet. */
    JsonNode awaitType(String type) {
        return await(m -> type.equals(m.path("type").asText()));
    }

    boolean awaitClosedByServer() throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (!closedByServer && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return closedByServer;
    }

    private JsonNode await(Predicate<JsonNode> matcher) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        synchronized (inbox) {
            while (true) {
                for (Iterator<JsonNode> it = inbox.iterator(); it.hasNext(); ) {
                    JsonNode m = it.next();
                    if (matcher.test(m)) {
                        it.remove();
                        return m;
                    }
                }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new AssertionError("Timed out waiting for message. Inbox: " + inbox);
                }
                try {
                    inbox.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
        }
    }

    protected void received(String text) {
        try {
            JsonNode node = mapper.readTree(text);
            synchronized (inbox) {
                inbox.add(node);
                inbox.notifyAll();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void serverClosed() {
        closedByServer = true;
    }

    private static final class Tcp extends TestClient {
        private final Socket socket;
        private final Writer out;

        Tcp(int port) throws IOException {
            socket = new Socket("localhost", port);
            out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            Thread.ofVirtual().start(() -> {
                try (var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        received(line);
                    }
                } catch (IOException ignored) {
                    // socket closed
                }
                serverClosed();
            });
        }

        @Override
        synchronized void sendRaw(String text) {
            try {
                out.write(text + "\n");
                out.flush();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static final class Ws extends TestClient {
        private final WebSocketSession session;

        Ws(String url) throws Exception {
            session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
                @Override
                protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                    received(message.getPayload());
                }

                @Override
                public void afterConnectionClosed(WebSocketSession s, org.springframework.web.socket.CloseStatus status) {
                    serverClosed();
                }
            }, url).get(5, TimeUnit.SECONDS);
        }

        @Override
        synchronized void sendRaw(String text) {
            try {
                session.sendMessage(new TextMessage(text));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws IOException {
            session.close();
        }
    }
}
