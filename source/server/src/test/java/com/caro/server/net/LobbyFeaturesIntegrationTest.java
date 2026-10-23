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

/** Quick match, online list, invitations and rematch, through real TCP connections. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LobbyFeaturesIntegrationTest {

    @Autowired
    TcpServer tcpServer;

    @Test
    void quickMatchPairsTwoWaitingPlayers() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("qa")); TestClient b = tcpLoggedIn(uniqueName("qb"))) {
            assertError(a.request("CANCEL_QUICK_MATCH", Map.of()), "NOT_IN_QUEUE");

            JsonNode waiting = a.ok("QUICK_MATCH", Map.of());
            assertThat(waiting.path("inQueue").asBoolean()).isTrue();
            assertThat(waiting.path("ratingRange").asInt()).isEqualTo(100);
            assertError(a.request("QUICK_MATCH", Map.of()), "ALREADY_IN_QUEUE");
            assertThat(presenceOf(b, id(a))).isEqualTo("IN_QUEUE");

            // same rating: b is paired with a as soon as b joins
            assertThat(b.ok("QUICK_MATCH", Map.of()).path("inQueue").asBoolean()).isFalse();
            assertThat(a.awaitType("QUEUE_STATUS").path("data").path("inQueue").asBoolean()).isFalse();

            String markA = a.awaitType("MATCH_START").path("data").path("yourMark").asText();
            String markB = b.awaitType("MATCH_START").path("data").path("yourMark").asText();
            assertThat(markA + markB).isIn("XO", "OX");
            assertThat(presenceOf(a, id(b))).isEqualTo("PLAYING");
            assertError(a.request("QUICK_MATCH", Map.of()), "ALREADY_IN_ROOM");
        }
    }

    @Test
    void cancellingTheQueue() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("qc"))) {
            a.ok("QUICK_MATCH", Map.of());
            a.ok("CANCEL_QUICK_MATCH", Map.of());
            assertThat(a.ok("GET_STATE", Map.of()).path("queue").path("inQueue").asBoolean()).isFalse();
        }
    }

    @Test
    void acceptedInvitationStartsAMatchWithTheInviterAsX() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("ia")); TestClient b = tcpLoggedIn(uniqueName("ib"))) {
            long bId = id(b);
            assertThat(presenceOf(a, bId)).isEqualTo("IDLE");

            String inviteId = a.ok("INVITE", Map.of("userId", bId)).path("inviteId").asText();
            JsonNode invited = b.awaitType("INVITED").path("data");
            assertThat(invited.path("inviteId").asText()).isEqualTo(inviteId);
            assertThat(invited.path("from").path("id").asLong()).isEqualTo(id(a));

            JsonNode room = b.ok("INVITE_REPLY", Map.of("inviteId", inviteId, "accept", true)).path("room");
            assertThat(room.path("status").asText()).isEqualTo("PLAYING");
            assertThat(a.awaitType("INVITE_CLOSED").path("data").path("reason").asText()).isEqualTo("ACCEPTED");
            assertThat(a.awaitType("MATCH_START").path("data").path("yourMark").asText()).isEqualTo("X");
            assertThat(b.awaitType("MATCH_START").path("data").path("yourMark").asText()).isEqualTo("O");
        }
    }

    @Test
    void invitationCanBeDeclinedCancelledOrExpire() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("ic")); TestClient b = tcpLoggedIn(uniqueName("id"))) {
            long bId = id(b);
            assertError(a.request("INVITE", Map.of("userId", id(a))), "VALIDATION_ERROR");
            assertError(a.request("INVITE", Map.of("userId", 999_999_999L)), "USER_OFFLINE");

            String first = a.ok("INVITE", Map.of("userId", bId)).path("inviteId").asText();
            assertError(a.request("INVITE", Map.of("userId", bId)), "INVITE_PENDING");
            b.ok("INVITE_REPLY", Map.of("inviteId", first, "accept", false));
            assertThat(a.awaitType("INVITE_CLOSED").path("data").path("reason").asText()).isEqualTo("DECLINED");

            String second = a.ok("INVITE", Map.of("userId", bId)).path("inviteId").asText();
            a.ok("CANCEL_INVITE", Map.of("inviteId", second));
            assertThat(b.awaitType("INVITE_CLOSED", d -> d.path("inviteId").asText().equals(second))
                    .path("data").path("reason").asText()).isEqualTo("CANCELLED");
            assertError(b.request("INVITE_REPLY", Map.of("inviteId", second, "accept", true)), "INVITE_NOT_FOUND");

            // caro.invite.timeout is 2 s in the test profile
            String third = a.ok("INVITE", Map.of("userId", bId)).path("inviteId").asText();
            assertThat(a.awaitType("INVITE_CLOSED", d -> d.path("inviteId").asText().equals(third))
                    .path("data").path("reason").asText()).isEqualTo("EXPIRED");
            assertThat(b.awaitType("INVITE_CLOSED", d -> d.path("inviteId").asText().equals(third))
                    .path("data").path("reason").asText()).isEqualTo("EXPIRED");
        }
    }

    @Test
    void invitationIsClosedWhenTheInvitedPlayerGoesOffline() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("ie"))) {
            TestClient b = tcpLoggedIn(uniqueName("if"));
            a.ok("INVITE", Map.of("userId", id(b)));
            b.close();
            assertThat(a.awaitType("INVITE_CLOSED").path("data").path("reason").asText()).isEqualTo("UNAVAILABLE");
        }
    }

    @Test
    void acceptingAnInvitationLeavesTheQueue() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("ig")); TestClient b = tcpLoggedIn(uniqueName("ih"))) {
            b.ok("QUICK_MATCH", Map.of());
            String inviteId = a.ok("INVITE", Map.of("userId", id(b))).path("inviteId").asText();
            b.awaitType("INVITED");
            b.ok("INVITE_REPLY", Map.of("inviteId", inviteId, "accept", true));
            assertThat(b.awaitType("QUEUE_STATUS").path("data").path("inQueue").asBoolean()).isFalse();
            b.awaitType("MATCH_START");
        }
    }

    @Test
    void rematchStartsWhenBothAskAndSwapsMarks() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("ra")); TestClient b = tcpLoggedIn(uniqueName("rb"))) {
            long aId = id(a);
            String roomId = a.ok("CREATE_ROOM", Map.of()).path("room").path("id").asText();
            b.ok("JOIN_ROOM", Map.of("roomId", roomId));
            JsonNode first = a.awaitType("MATCH_START").path("data");
            assertThat(first.path("yourMark").asText()).isEqualTo("X");
            assertError(a.request("REMATCH", Map.of()), "REMATCH_NOT_AVAILABLE");

            a.ok("RESIGN", Map.of("matchId", first.path("matchId").asText()));
            b.awaitType("MATCH_END");

            JsonNode asked = a.ok("REMATCH", Map.of()).path("room");
            assertThat(asked.path("status").asText()).isEqualTo("FINISHED");
            assertThat(asked.path("rematchRequests")).hasSize(1);
            assertThat(b.awaitType("ROOM_UPDATE", d -> d.path("room").path("rematchRequests").size() == 1)
                    .path("data").path("room").path("rematchRequests").get(0).asLong()).isEqualTo(aId);

            b.ok("REMATCH", Map.of());
            JsonNode second = a.awaitType("MATCH_START").path("data");
            assertThat(second.path("matchId").asText()).isNotEqualTo(first.path("matchId").asText());
            assertThat(second.path("yourMark").asText()).isEqualTo("O");
            String secondId = second.path("matchId").asText();
            assertThat(b.awaitType("MATCH_START", d -> d.path("matchId").asText().equals(secondId))
                    .path("data").path("yourMark").asText()).isEqualTo("X");
        }
    }

    // ------------------------------------------------------------------ helpers

    private TestClient tcpLoggedIn(String username) throws Exception {
        TestClient client = TestClient.tcp(tcpServer.getLocalPort());
        client.ok("REGISTER", Map.of("username", username, "password", "secret1"));
        client.ok("LOGIN", Map.of("username", username, "password", "secret1"));
        return client;
    }

    private static long id(TestClient client) {
        return client.ok("GET_PROFILE", Map.of()).path("player").path("id").asLong();
    }

    /** Presence of another user, as seen by {@code viewer} in LIST_ONLINE. */
    private static String presenceOf(TestClient viewer, long userId) {
        for (JsonNode u : viewer.ok("LIST_ONLINE", Map.of()).path("users")) {
            if (u.path("id").asLong() == userId) {
                return u.path("status").asText();
            }
        }
        return "OFFLINE";
    }

    private static void assertError(JsonNode reply, String code) {
        assertThat(reply.path("type").asText()).as("%s", reply).isEqualTo("ERROR");
        assertThat(reply.path("data").path("code").asText()).isEqualTo(code);
    }

    private static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
