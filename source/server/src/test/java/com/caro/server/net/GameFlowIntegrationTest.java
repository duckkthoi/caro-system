package com.caro.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.caro.server.match.MatchRecord;
import com.caro.server.match.MatchRecordRepository;
import com.caro.server.net.tcp.TcpServer;
import com.caro.server.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * End-to-end tests through the real transports: raw TCP sockets and WebSocket, against an in-memory database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GameFlowIntegrationTest {

    @LocalServerPort
    int httpPort;

    @Autowired
    TcpServer tcpServer;

    @Autowired
    MatchRecordRepository matchRecords;

    @Autowired
    UserRepository users;

    private static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private TestClient tcpLoggedIn(String username) throws Exception {
        TestClient client = TestClient.tcp(tcpServer.getLocalPort());
        client.ok("REGISTER", Map.of("username", username, "password", "secret1"));
        client.ok("LOGIN", Map.of("username", username, "password", "secret1"));
        return client;
    }

    @Test
    void tcpPlayerBeatsWebPlayerAndTheResultIsStored() throws Exception {
        String alice = uniqueName("alice");
        String bob = uniqueName("bob");

        // bob uses the web flow: REST register + login, then WebSocket with AUTH <token>
        RestClient rest = RestClient.create("http://localhost:" + httpPort);
        rest.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", bob, "password", "secret1")).retrieve().toBodilessEntity();
        JsonNode login = rest.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", bob, "password", "secret1")).retrieve().body(JsonNode.class);

        try (TestClient a = tcpLoggedIn(alice);
             TestClient b = TestClient.ws("ws://localhost:" + httpPort + "/ws")) {
            b.ok("AUTH", Map.of("token", login.get("token").asText()));

            String roomId = a.ok("CREATE_ROOM", Map.of("name", "test room")).path("room").path("id").asText();
            JsonNode joined = b.ok("JOIN_ROOM", Map.of("roomId", roomId)).path("room");
            assertThat(joined.path("status").asText()).isEqualTo("PLAYING");

            JsonNode startA = a.awaitType("MATCH_START").path("data");
            JsonNode startB = b.awaitType("MATCH_START").path("data");
            assertThat(startA.path("yourMark").asText()).isEqualTo("X");
            assertThat(startB.path("yourMark").asText()).isEqualTo("O");
            String matchId = startA.path("matchId").asText();

            // a wrong-turn move is rejected by the server
            JsonNode early = b.request("MOVE", Map.of("matchId", matchId, "x", 0, "y", 0));
            assertThat(early.path("data").path("code").asText()).isEqualTo("NOT_YOUR_TURN");

            for (int i = 0; i < 4; i++) {
                a.ok("MOVE", Map.of("matchId", matchId, "x", i, "y", 7));
                b.ok("MOVE", Map.of("matchId", matchId, "x", i, "y", 8));
            }
            a.ok("MOVE", Map.of("matchId", matchId, "x", 4, "y", 7));

            for (TestClient c : new TestClient[] {a, b}) {
                JsonNode end = c.awaitType("MATCH_END").path("data");
                assertThat(end.path("result").asText()).isEqualTo("X_WIN");
                assertThat(end.path("reason").asText()).isEqualTo("FIVE_IN_ROW");
                assertThat(end.path("winLine")).hasSize(5);
            }

            MatchRecord saved = matchRecords.findById(UUID.fromString(matchId)).orElseThrow();
            assertThat(saved.getTotalMoves()).isEqualTo(9);
            assertThat(users.findByUsername(alice).orElseThrow().getRating()).isEqualTo(1016);
            assertThat(users.findByUsername(bob).orElseThrow().getRating()).isEqualTo(984);
        }
    }

    @Test
    void disconnectedPlayerCanComeBackAndContinue() throws Exception {
        String alice = uniqueName("alice");
        String bob = uniqueName("bob");
        try (TestClient a = tcpLoggedIn(alice)) {
            TestClient b = tcpLoggedIn(bob);
            String roomId = a.ok("CREATE_ROOM", Map.of()).path("room").path("id").asText();
            b.ok("JOIN_ROOM", Map.of("roomId", roomId));
            String matchId = a.awaitType("MATCH_START").path("data").path("matchId").asText();
            a.ok("MOVE", Map.of("matchId", matchId, "x", 7, "y", 7));

            b.close(); // bob's connection drops
            JsonNode gone = a.awaitType("OPPONENT_DISCONNECTED").path("data");
            assertThat(gone.path("graceDeadline").asLong()).isPositive();
            JsonNode paused = a.request("MOVE", Map.of("matchId", matchId, "x", 1, "y", 1));
            assertThat(paused.path("data").path("code").asText()).isEqualTo("MATCH_NOT_IN_PROGRESS");

            try (TestClient b2 = TestClient.tcp(tcpServer.getLocalPort())) {
                b2.ok("LOGIN", Map.of("username", bob, "password", "secret1"));
                JsonNode state = b2.awaitType("MATCH_STATE").path("data");
                assertThat(state.path("status").asText()).isEqualTo("IN_PROGRESS");
                assertThat(state.path("yourMark").asText()).isEqualTo("O");
                assertThat(state.path("board").get(7).asText()).isEqualTo(".......X.......");
                a.awaitType("OPPONENT_RECONNECTED");

                b2.ok("MOVE", Map.of("matchId", matchId, "x", 8, "y", 8));
                assertThat(a.awaitType("MOVE_APPLIED").path("data").path("moveNo").asInt()).isEqualTo(1);
                assertThat(a.awaitType("MOVE_APPLIED").path("data").path("moveNo").asInt()).isEqualTo(2);
            }
        }
    }

    @Test
    void secondLoginKicksTheFirstSession() throws Exception {
        String carol = uniqueName("carol");
        try (TestClient first = tcpLoggedIn(carol);
             TestClient second = TestClient.tcp(tcpServer.getLocalPort())) {
            second.ok("LOGIN", Map.of("username", carol, "password", "secret1"));
            assertThat(first.awaitType("KICKED").path("data").path("reason").asText()).isEqualTo("LOGGED_IN_ELSEWHERE");
            assertThat(first.awaitClosedByServer()).isTrue();
            second.ok("LIST_ROOMS", Map.of());
        }
    }

    @Test
    void protocolErrorsAreReported() throws Exception {
        try (TestClient c = TestClient.tcp(tcpServer.getLocalPort())) {
            c.sendRaw("this is not json");
            assertThat(c.awaitType("ERROR").path("data").path("code").asText()).isEqualTo("BAD_REQUEST");

            assertThat(c.request("LIST_ROOMS", Map.of()).path("data").path("code").asText()).isEqualTo("UNAUTHORIZED");
            assertThat(c.request("FLY", Map.of()).path("data").path("code").asText()).isEqualTo("UNKNOWN_TYPE");
            assertThat(c.request("LOGIN", Map.of("username", "nobody", "password", "x")).path("data").path("code")
                    .asText()).isEqualTo("INVALID_CREDENTIALS");

            JsonNode pong = c.request("PING", Map.of());
            assertThat(pong.path("type").asText()).isEqualTo("PONG");
            assertThat(pong.path("data").path("serverTime").asLong()).isPositive();
        }
    }
}
