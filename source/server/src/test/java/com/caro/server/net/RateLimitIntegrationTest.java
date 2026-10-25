package com.caro.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.caro.server.net.tcp.TcpServer;
import com.fasterxml.jackson.databind.JsonNode;

/** Rate limits (docs/rules.md, section 13) with tiny buckets that barely refill, so the limits are hit quickly. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "caro.rate-limit.burst=6",
        "caro.rate-limit.per-second=0.001",
        "caro.rate-limit.chat-burst=2",
        "caro.rate-limit.chat-per-second=0.001"})
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired
    TcpServer tcpServer;

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
}
