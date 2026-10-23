package com.caro.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.caro.server.net.tcp.TcpServer;
import com.fasterxml.jackson.databind.JsonNode;

/** REST API (history, profile, leaderboard, admin) and the matching socket queries, against a real server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RestApiIntegrationTest {

    @LocalServerPort
    int httpPort;

    @Autowired
    TcpServer tcpServer;

    private RestClient rest;

    @BeforeEach
    void setUp() {
        rest = RestClient.create("http://localhost:" + httpPort);
    }

    @Test
    void historyProfileReplayAndLeaderboardAfterAMatch() throws Exception {
        String alice = uniqueName("alice");
        String bob = uniqueName("bob");
        try (TestClient a = tcpLoggedIn(alice); TestClient b = tcpLoggedIn(bob)) {
            long aliceId = a.ok("GET_PROFILE", Map.of()).path("player").path("id").asLong();
            long bobId = b.ok("GET_PROFILE", Map.of()).path("player").path("id").asLong();
            String matchId = playXWins(a, b);
            String token = login(alice);

            JsonNode profile = get(token, "/api/users/{id}", aliceId).getBody();
            assertThat(profile.path("player").path("rating").asInt()).isEqualTo(1016);
            assertThat(profile.path("online").asBoolean()).isTrue();
            assertThat(profile.path("rank").asInt()).isPositive();
            assertThat(profile.path("stats").path("played").asInt()).isEqualTo(1);
            assertThat(profile.path("stats").path("wins").asInt()).isEqualTo(1);
            assertThat(profile.path("stats").path("winRate").asDouble()).isEqualTo(100.0);

            JsonNode history = get(token, "/api/users/{id}/matches", bobId).getBody();
            assertThat(history.path("totalItems").asInt()).isEqualTo(1);
            JsonNode item = history.path("items").get(0);
            assertThat(item.path("matchId").asText()).isEqualTo(matchId);
            assertThat(item.path("mark").asText()).isEqualTo("O");
            assertThat(item.path("outcome").asText()).isEqualTo("LOSS");
            assertThat(item.path("ratingChange").asInt()).isEqualTo(-16);
            assertThat(item.path("opponent").path("username").asText()).isEqualTo(alice);

            JsonNode detail = get(token, "/api/matches/{id}", matchId).getBody();
            assertThat(detail.path("match").path("result").asText()).isEqualTo("X_WIN");
            assertThat(detail.path("match").path("ratingChange").path("X").asInt()).isEqualTo(16);
            assertThat(detail.path("moves")).hasSize(9);
            assertThat(detail.path("moves").get(0).path("mark").asText()).isEqualTo("X");
            assertThat(detail.path("winLine")).hasSize(5);

            JsonNode board = get(token, "/api/leaderboard?size=100").getBody();
            int aliceRank = rankIn(board, alice);
            int bobRank = rankIn(board, bob);
            assertThat(aliceRank).isPositive().isLessThan(bobRank).isEqualTo(profile.path("rank").asInt());
            assertThat(rankIn(board, "admin")).as("admin is not ranked").isZero();

            // the desktop client gets the same data over the socket
            JsonNode overSocket = b.ok("GET_HISTORY", Map.of());
            assertThat(overSocket.path("items").get(0).path("outcome").asText()).isEqualTo("LOSS");
            assertThat(b.ok("GET_MATCH", Map.of("matchId", matchId)).path("moves")).hasSize(9);
            assertThat(b.ok("GET_LEADERBOARD", Map.of("size", 100)).path("items").size())
                    .isEqualTo(board.path("items").size());
            assertThat(b.request("GET_MATCH", Map.of("matchId", "nope")).path("data").path("code").asText())
                    .isEqualTo("BAD_REQUEST");
        }
    }

    @Test
    void restErrorsUseTheCommonErrorBody() throws Exception {
        String carol = uniqueName("carol");
        register(carol);
        String token = login(carol);

        assertError(get(null, "/api/leaderboard"), 401, "UNAUTHORIZED");
        assertError(get("not-a-jwt", "/api/leaderboard"), 401, "UNAUTHORIZED");
        assertError(get(token, "/api/admin/stats"), 403, "FORBIDDEN");
        assertError(get(token, "/api/matches/not-a-uuid"), 400, "BAD_REQUEST");
        assertError(get(token, "/api/matches/{id}", UUID.randomUUID()), 404, "MATCH_NOT_FOUND");
        assertError(get(token, "/api/users/999999999"), 404, "USER_NOT_FOUND");
        assertError(get(token, "/api/leaderboard?size=1000"), 400, "VALIDATION_ERROR");
        assertError(get(token, "/api/nothing-here"), 404, "NOT_FOUND");
    }

    @Test
    void adminLocksAnOnlinePlayerAndUnlocksThem() throws Exception {
        String dave = uniqueName("dave");
        String erin = uniqueName("erin");
        String admin = login("admin", "admin123");
        try (TestClient d = tcpLoggedIn(dave); TestClient e = tcpLoggedIn(erin)) {
            long daveId = d.ok("GET_PROFILE", Map.of()).path("player").path("id").asLong();
            String daveToken = login(dave);
            String roomId = d.ok("CREATE_ROOM", Map.of()).path("room").path("id").asText();
            e.ok("JOIN_ROOM", Map.of("roomId", roomId));
            e.awaitType("MATCH_START");

            JsonNode found = get(admin, "/api/admin/users?q=" + dave).getBody();
            assertThat(found.path("totalItems").asInt()).isEqualTo(1);
            assertThat(found.path("items").get(0).path("online").asBoolean()).isTrue();
            JsonNode stats = get(admin, "/api/admin/stats").getBody();
            assertThat(stats.path("activeMatches").asInt()).isPositive();
            assertThat(stats.path("onlineUsers").asInt()).isGreaterThanOrEqualTo(2);

            JsonNode locked = post(admin, "/api/admin/users/{id}/lock", daveId).getBody();
            assertThat(locked.path("status").asText()).isEqualTo("LOCKED");

            // kicked from the socket, which the match treats as a disconnect
            assertThat(d.awaitType("KICKED").path("data").path("reason").asText()).isEqualTo("ACCOUNT_LOCKED");
            assertThat(d.awaitClosedByServer()).isTrue();
            assertThat(e.awaitType("OPPONENT_DISCONNECTED").path("data").path("userId").asLong()).isEqualTo(daveId);

            // cannot come back, and the old token stops working too
            try (TestClient again = TestClient.tcp(tcpServer.getLocalPort())) {
                assertThat(again.request("LOGIN", Map.of("username", dave, "password", "secret1"))
                        .path("data").path("code").asText()).isEqualTo("ACCOUNT_LOCKED");
            }
            assertError(get(daveToken, "/api/auth/me"), 401, "UNAUTHORIZED");

            long adminId = get(admin, "/api/auth/me").getBody().path("id").asLong();
            assertError(post(admin, "/api/admin/users/{id}/lock", adminId), 403, "FORBIDDEN");

            assertThat(post(admin, "/api/admin/users/{id}/unlock", daveId).getBody().path("status").asText())
                    .isEqualTo("ACTIVE");
            assertThat(login(dave)).isNotBlank();
        }
    }

    // ------------------------------------------------------------------ helpers

    /** alice (X) wins with a horizontal five; returns the match id. */
    private static String playXWins(TestClient x, TestClient o) {
        String roomId = x.ok("CREATE_ROOM", Map.of()).path("room").path("id").asText();
        o.ok("JOIN_ROOM", Map.of("roomId", roomId));
        String matchId = x.awaitType("MATCH_START").path("data").path("matchId").asText();
        for (int i = 0; i < 4; i++) {
            x.ok("MOVE", Map.of("matchId", matchId, "x", i, "y", 7));
            o.ok("MOVE", Map.of("matchId", matchId, "x", i, "y", 8));
        }
        // the OK of the winning move is only sent once the result is stored
        x.ok("MOVE", Map.of("matchId", matchId, "x", 4, "y", 7));
        x.awaitType("MATCH_END");
        o.awaitType("MATCH_END");
        return matchId;
    }

    private static int rankIn(JsonNode leaderboard, String username) {
        for (JsonNode entry : leaderboard.path("items")) {
            if (entry.path("player").path("username").asText().equals(username)) {
                return entry.path("rank").asInt();
            }
        }
        return 0;
    }

    private static void assertError(ResponseEntity<JsonNode> response, int status, String code) {
        assertThat(response.getStatusCode().value()).as("%s", response.getBody()).isEqualTo(status);
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
    }

    private TestClient tcpLoggedIn(String username) throws Exception {
        TestClient client = TestClient.tcp(tcpServer.getLocalPort());
        client.ok("REGISTER", Map.of("username", username, "password", "secret1"));
        client.ok("LOGIN", Map.of("username", username, "password", "secret1"));
        return client;
    }

    private void register(String username) {
        rest.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", "secret1")).retrieve().toBodilessEntity();
    }

    private String login(String username) {
        return login(username, "secret1");
    }

    private String login(String username, String password) {
        return rest.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .retrieve().body(JsonNode.class).path("token").asText();
    }

    /** GET that returns error responses instead of throwing. */
    private ResponseEntity<JsonNode> get(String token, String uri, Object... vars) {
        return rest.get().uri(uri, vars)
                .headers(h -> {
                    if (token != null) {
                        h.setBearerAuth(token);
                    }
                })
                .retrieve().onStatus(s -> true, (req, res) -> { }).toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String token, String uri, Object... vars) {
        return rest.post().uri(uri, vars).headers(h -> h.setBearerAuth(token))
                .retrieve().onStatus(s -> true, (req, res) -> { }).toEntity(JsonNode.class);
    }

    private static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
