package com.caro.client.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/** Connection against a fake server that speaks the line-based protocol. */
class ConnectionTest {

    private ServerSocket serverSocket;
    private final ExecutorService ui = Executors.newSingleThreadExecutor();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final BlockingQueue<String> closed = new LinkedBlockingQueue<>();
    private Connection connection;
    private Socket serverSide;
    private BufferedReader fromClient;
    private PrintWriter toClient;

    @BeforeEach
    void setUp() throws Exception {
        serverSocket = new ServerSocket(0);
        CompletableFuture<Socket> accepted = CompletableFuture.supplyAsync(() -> {
            try {
                return serverSocket.accept();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        connection = Connection.open("localhost", serverSocket.getLocalPort(), ui, new Connection.Listener() {
            @Override
            public void onEvent(String type, JsonNode data) {
                events.add(type);
            }

            @Override
            public void onClosed(String reason) {
                closed.add(reason);
            }
        });
        serverSide = accepted.get(5, TimeUnit.SECONDS);
        fromClient = new BufferedReader(new InputStreamReader(serverSide.getInputStream(), StandardCharsets.UTF_8));
        toClient = new PrintWriter(new OutputStreamWriter(serverSide.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
        serverSocket.close();
        ui.shutdownNow();
    }

    /** Reads client lines until one of this type (the client also sends PINGs on its own). */
    private JsonNode next(String type) throws Exception {
        while (true) {
            JsonNode message = Json.MAPPER.readTree(fromClient.readLine());
            if (type.equals(message.path("type").asText())) {
                return message;
            }
        }
    }

    @Test
    void replyCompletesTheRequestWithTheSameReqId() throws Exception {
        CompletableFuture<JsonNode> first = connection.request("LIST_ROOMS", Map.of());
        CompletableFuture<JsonNode> second = connection.request("GET_STATE", Map.of());
        JsonNode r1 = next("LIST_ROOMS");
        JsonNode r2 = next("GET_STATE");
        assertTrue(!r1.path("reqId").asText().equals(r2.path("reqId").asText()), "reqIds must differ");

        // answers in reverse order, with an event in between
        toClient.println("{\"type\":\"OK\",\"reqId\":\"" + r2.path("reqId").asText() + "\",\"data\":{\"n\":2}}");
        toClient.println("{\"type\":\"ROOM_LIST\",\"data\":{\"rooms\":[]}}");
        toClient.println("{\"type\":\"OK\",\"reqId\":\"" + r1.path("reqId").asText() + "\",\"data\":{\"n\":1}}");

        assertEquals(1, first.get(5, TimeUnit.SECONDS).path("n").asInt());
        assertEquals(2, second.get(5, TimeUnit.SECONDS).path("n").asInt());
        assertEquals(List.of("ROOM_LIST"), events);
    }

    @Test
    void errorReplyFailsTheRequestWithTheServerCode() throws Exception {
        CompletableFuture<JsonNode> move = connection.request("MOVE", Map.of("x", 1));
        String reqId = next("MOVE").path("reqId").asText();
        toClient.println("{\"type\":\"ERROR\",\"reqId\":\"" + reqId
                + "\",\"data\":{\"code\":\"NOT_YOUR_TURN\",\"message\":\"Chưa đến lượt của bạn\"}}");
        try {
            move.get(5, TimeUnit.SECONDS);
            fail("expected an error");
        } catch (ExecutionException e) {
            ServerException error = assertInstanceOf(ServerException.class, e.getCause());
            assertEquals("NOT_YOUR_TURN", error.code());
            assertEquals("Chưa đến lượt của bạn", error.getMessage());
        }
    }

    @Test
    void sendsPingRightAwayAndUsesPongForTheServerClock() throws Exception {
        JsonNode ping = next("PING");
        long serverTime = System.currentTimeMillis() + 60_000; // server clock one minute ahead
        toClient.println("{\"type\":\"PONG\",\"reqId\":\"" + ping.path("reqId").asText()
                + "\",\"data\":{\"serverTime\":" + serverTime + "}}");
        long deadline = System.currentTimeMillis() + 5000;
        while (connection.serverNow() - System.currentTimeMillis() < 55_000) {
            if (System.currentTimeMillis() > deadline) {
                fail("clock offset not applied");
            }
            Thread.sleep(20);
        }
    }

    @Test
    void serverClosingFailsPendingRequestsAndNotifiesOnce() throws Exception {
        CompletableFuture<JsonNode> pending = connection.request("GET_STATE", Map.of());
        next("GET_STATE");
        serverSide.close();

        assertEquals("Server đã đóng kết nối", closed.poll(5, TimeUnit.SECONDS));
        try {
            pending.get(5, TimeUnit.SECONDS);
            fail("expected DISCONNECTED");
        } catch (ExecutionException e) {
            assertEquals(ServerException.DISCONNECTED, ((ServerException) e.getCause()).code());
        }
        connection.close();
        assertEquals(null, closed.poll(300, TimeUnit.MILLISECONDS), "onClosed must be called only once");
        assertTrue(!connection.isOpen());
    }
}
