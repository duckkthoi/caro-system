package com.caro.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

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

/** Rate limits (docs/rules.md, section 13) with tiny buckets that barely refill, so the limits are hit quickly. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "caro.rate-limit.burst=6",
        "caro.rate-limit.per-second=0.001",
        "caro.rate-limit.chat-burst=2",
        "caro.rate-limit.chat-per-second=0.001",
        "caro.rate-limit.auth-burst=4",
        "caro.rate-limit.auth-per-second=0.001"})
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired
    TcpServer tcpServer;

    @LocalServerPort
    int httpPort;

    @Test
    void tooManyMessagesAreRejectedButPingAlwaysWorks() throws Exception {
        try (TestClient client = TestClient.tcp(tcpServer.getLocalPort())) {
            String username = "rl_" + UUID.randomUUID().toString().substring(0, 8);
            client.ok("REGISTER", Map.of("username", username, "password", "secret1")); // 1
            client.ok("LOGIN", Map.of("username", username, "password", "secret1"));    // 2
            client.ok("LIST_ROOMS", Map.of());                                          // 3
            client.ok("LIST_ROOMS", Map.of());                                          // 4
            client.ok("LIST_ROOMS", Map.of());                                          // 5
            client.ok("LIST_ROOMS", Map.of());                                          // 6
            assertError(client.request("LIST_ROOMS", Map.of()), "RATE_LIMITED");
            client.sendRaw("this is not json");                                         // still counted
            assertThat(client.awaitType("ERROR").path("data").path("code").asText()).isEqualTo("RATE_LIMITED");

            assertThat(client.request("PING", Map.of()).path("type").asText()).as("PING is never limited")
                    .isEqualTo("PONG");
        }
    }

    @Test
    void chatHasItsOwnStricterLimit() throws Exception {
        try (TestClient client = TestClient.tcp(tcpServer.getLocalPort())) {
            String username = "rc_" + UUID.randomUUID().toString().substring(0, 8);
            client.ok("REGISTER", Map.of("username", username, "password", "secret1"));
            client.ok("LOGIN", Map.of("username", username, "password", "secret1"));
            client.ok("CREATE_ROOM", Map.of());
            client.ok("CHAT", Map.of("text", "một"));
            client.ok("CHAT", Map.of("text", "hai"));
            assertError(client.request("CHAT", Map.of("text", "ba")), "RATE_LIMITED");
        }
    }

    private static void assertError(JsonNode reply, String code) {
        assertThat(reply.path("type").asText()).as("%s", reply).isEqualTo("ERROR");
        assertThat(reply.path("data").path("code").asText()).isEqualTo(code);
    }

    @Test
    void loginAttemptsAreLimitedPerAddress() {
        RestClient rest = RestClient.create("http://localhost:" + httpPort);
        // the test runs on 127.0.0.1, which the server treats as a proxy: X-Real-IP picks the "client"
        for (int i = 0; i < 4; i++) {
            assertThat(attempt(rest, "203.0.113.50").getStatusCode().value()).isEqualTo(401);
        }
        ResponseEntity<JsonNode> blocked = attempt(rest, "203.0.113.50");
        assertThat(blocked.getStatusCode().value()).isEqualTo(429);
        assertThat(blocked.getBody().path("code").asText()).isEqualTo("RATE_LIMITED");
        // another address has its own bucket
        assertThat(attempt(rest, "203.0.113.51").getStatusCode().value()).isEqualTo(401);
    }

    private static ResponseEntity<JsonNode> attempt(RestClient rest, String ip) {
        return rest.post().uri("/api/auth/login").header("X-Real-IP", ip).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "nobody_here", "password", "wrong-password"))
                .retrieve().onStatus(s -> true, (req, res) -> { }).toEntity(JsonNode.class);
    }
}
