package com.caro.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.caro.server.net.tcp.TcpServer;
import com.fasterxml.jackson.databind.JsonNode;

/** Spectators, room chat, the "block two ends" rule and matches against the bot, through real TCP connections. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomExtrasIntegrationTest {

    @Autowired
    TcpServer tcpServer;

    // ------------------------------------------------------------------ spectators and chat

    @Test
    void spectatorWatchesTheMatchAndEveryoneCanChat() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("sa")); TestClient b = tcpLoggedIn(uniqueName("sb"));
             TestClient c = tcpLoggedIn(uniqueName("sc"))) {
            long cId = id(c);
            String roomId = a.ok("CREATE_ROOM", Map.of()).path("room").path("id").asText();
            b.ok("JOIN_ROOM", Map.of("roomId", roomId));
            String matchId = a.awaitType("MATCH_START").path("data").path("matchId").asText();
            a.ok("MOVE", Map.of("matchId", matchId, "x", 7, "y", 7));

            JsonNode room = c.ok("SPECTATE", Map.of("roomId", roomId)).path("room");
            assertThat(room.path("spectators")).hasSize(1);
            JsonNode state = c.awaitType("MATCH_STATE").path("data");
            assertThat(state.path("yourMark").isNull()).isTrue();
            assertThat(state.path("moveCount").asInt()).isEqualTo(1);
            assertThat(a.awaitType("ROOM_UPDATE", d -> d.path("room").path("spectators").size() == 1)).isNotNull();
            assertThat(presenceOf(a, cId)).isEqualTo("SPECTATING");
            assertThat(c.ok("GET_STATE", Map.of()).path("spectating").asBoolean()).isTrue();

            // a spectator cannot play, the players' moves reach them
            assertError(c.request("MOVE", Map.of("matchId", matchId, "x", 0, "y", 0)), "MATCH_NOT_FOUND");
            b.ok("MOVE", Map.of("matchId", matchId, "x", 8, "y", 8));
            assertThat(c.awaitType("MOVE_APPLIED").path("data").path("mark").asText()).isEqualTo("O");

            c.ok("CHAT", Map.of("text", "  Hay quá\nđi tiếp đi  "));
            for (TestClient member : new TestClient[] {a, b, c}) {
                JsonNode chat = member.awaitType("CHAT_MESSAGE").path("data");
                assertThat(chat.path("text").asText()).isEqualTo("Hay quá đi tiếp đi");
                assertThat(chat.path("spectator").asBoolean()).isTrue();
                assertThat(chat.path("from").path("id").asLong()).isEqualTo(cId);
            }
            a.ok("CHAT", Map.of("text", "cảm ơn"));
            assertThat(c.awaitType("CHAT_MESSAGE").path("data").path("spectator").asBoolean()).isFalse();
            assertError(c.request("CHAT", Map.of("text", "   ")), "VALIDATION_ERROR");
            assertError(c.request("CHAT", Map.of("text", "x".repeat(201))), "VALIDATION_ERROR");

            a.ok("RESIGN", Map.of("matchId", matchId));
            assertThat(c.awaitType("MATCH_END").path("data").path("result").asText()).isEqualTo("O_WIN");

            c.ok("LEAVE_ROOM", Map.of());
            assertThat(a.awaitType("ROOM_UPDATE", d -> d.path("room").path("spectators").isEmpty())).isNotNull();
            assertError(c.request("LEAVE_ROOM", Map.of()), "NOT_IN_ROOM");
            assertError(c.request("CHAT", Map.of("text", "còn ai không")), "NOT_IN_ROOM");
        }
    }

    @Test
    void spectatorIsSentBackToTheLobbyWhenTheRoomCloses() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("se")); TestClient c = tcpLoggedIn(uniqueName("sf"))) {
            String roomId = a.ok("CREATE_ROOM", Map.of()).path("room").path("id").asText();
            c.ok("SPECTATE", Map.of("roomId", roomId));
            a.ok("LEAVE_ROOM", Map.of());
            assertThat(c.awaitType("SPECTATE_ENDED").path("data").path("roomId").asText()).isEqualTo(roomId);
            JsonNode state = c.ok("GET_STATE", Map.of());
            assertThat(state.path("room").isNull()).isTrue();
            assertThat(state.path("spectating").asBoolean()).isFalse();
        }
    }

    @Test
    void spectatorCanStillTakeASeatAndPlayersCannotSpectate() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("sg")); TestClient c = tcpLoggedIn(uniqueName("sh"))) {
            String roomId = a.ok("CREATE_ROOM", Map.of()).path("room").path("id").asText();
            assertError(a.request("SPECTATE", Map.of("roomId", roomId)), "ALREADY_IN_ROOM");
            assertError(c.request("SPECTATE", Map.of("roomId", "R-none")), "ROOM_NOT_FOUND");

            c.ok("SPECTATE", Map.of("roomId", roomId));
            JsonNode room = c.ok("JOIN_ROOM", Map.of("roomId", roomId)).path("room");
            assertThat(room.path("players")).hasSize(2);
            assertThat(room.path("spectators")).isEmpty();
            assertThat(c.awaitType("MATCH_START").path("data").path("yourMark").asText()).isEqualTo("O");
        }
    }

    // ------------------------------------------------------------------ block two ends

    @Test
    void blockTwoEndsRoomKeepsPlayingAfterABlockedFiveAndStoresTheRule() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("ba")); TestClient b = tcpLoggedIn(uniqueName("bb"))) {
            JsonNode created = a.ok("CREATE_ROOM", Map.of("blockTwoEnds", true)).path("room");
            assertThat(created.path("blockTwoEnds").asBoolean()).isTrue();
            b.ok("JOIN_ROOM", Map.of("roomId", created.path("id").asText()));
            JsonNode start = a.awaitType("MATCH_START").path("data");
            assertThat(start.path("blockTwoEnds").asBoolean()).isTrue();
            String matchId = start.path("matchId").asText();

            int[][] moves = {{3, 0}, {2, 0}, {4, 0}, {8, 0}, {5, 0}, {0, 5}, {6, 0}, {1, 5}, {7, 0}};
            for (int i = 0; i < moves.length; i++) {
                TestClient player = i % 2 == 0 ? a : b;
                player.ok("MOVE", Map.of("matchId", matchId, "x", moves[i][0], "y", moves[i][1]));
            }
            JsonNode state = a.ok("GET_STATE", Map.of()).path("match");
            assertThat(state.path("status").asText()).isEqualTo("IN_PROGRESS");
            assertThat(state.path("turn").asText()).isEqualTo("O");

            b.ok("RESIGN", Map.of("matchId", matchId));
            a.awaitType("MATCH_END");
            awaitStored(a, matchId);
            assertThat(a.ok("GET_MATCH", Map.of("matchId", matchId)).path("match").path("blockTwoEnds").asBoolean())
                    .isTrue();
        }
    }

    // ------------------------------------------------------------------ bot

    @Test
    void playingAgainstTheBotIsUnratedAndRematchSwapsMarks() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("pa")); TestClient c = tcpLoggedIn(uniqueName("pc"))) {
            assertError(a.request("PLAY_BOT", Map.of("level", "MEDIUM")), "BAD_REQUEST");
            JsonNode room = a.ok("PLAY_BOT", Map.of("level", "EASY")).path("room");
            assertThat(room.path("botLevel").asText()).isEqualTo("EASY");
            assertThat(room.path("status").asText()).isEqualTo("PLAYING");
            assertThat(room.path("players").get(1).path("id").asLong()).isEqualTo(-1);

            JsonNode start = a.awaitType("MATCH_START").path("data");
            assertThat(start.path("yourMark").asText()).isEqualTo("X");
            assertThat(start.path("rated").asBoolean()).isFalse();
            String matchId = start.path("matchId").asText();

            // others can watch, not join
            String roomId = room.path("id").asText();
            assertError(c.request("JOIN_ROOM", Map.of("roomId", roomId)), "ROOM_FULL");
            c.ok("SPECTATE", Map.of("roomId", roomId));

            a.ok("MOVE", Map.of("matchId", matchId, "x", 7, "y", 7));
            JsonNode reply = a.awaitType("MOVE_APPLIED", d -> d.path("mark").asText().equals("O")).path("data");
            assertThat(reply.path("nextTurn").asText()).isEqualTo("X");
            assertThat(c.awaitType("MOVE_APPLIED", d -> d.path("mark").asText().equals("O"))).isNotNull();

            a.ok("RESIGN", Map.of("matchId", matchId));
            JsonNode end = a.awaitType("MATCH_END").path("data");
            assertThat(end.path("ratingChange").path("X").asInt()).isZero();

            // the bot agrees at once and now plays X, so it moves first
            a.ok("REMATCH", Map.of());
            JsonNode second = a.awaitType("MATCH_START", d -> !d.path("matchId").asText().equals(matchId)).path("data");
            assertThat(second.path("yourMark").asText()).isEqualTo("O");
            assertThat(a.awaitType("MOVE_APPLIED", d -> d.path("matchId").asText()
                    .equals(second.path("matchId").asText())).path("data").path("mark").asText()).isEqualTo("X");

            a.ok("LEAVE_ROOM", Map.of()); // forfeits; the room has no human player left and closes
            assertThat(c.awaitType("SPECTATE_ENDED").path("data").path("roomId").asText()).isEqualTo(roomId);
            assertThat(a.ok("GET_HISTORY", Map.of()).path("totalItems").asInt()).isZero();
            assertThat(a.ok("GET_PROFILE", Map.of()).path("player").path("rating").asInt()).isEqualTo(1000);
        }
    }

    @Test
    void hardBotWinsAgainstAPlayerWhoIgnoresIt() throws Exception {
        try (TestClient a = tcpLoggedIn(uniqueName("ph"))) {
            a.ok("PLAY_BOT", Map.of());
            String matchId = a.awaitType("MATCH_START").path("data").path("matchId").asText();
            // the player only plays along the border, far from where the bot builds its line
            Deque<int[]> border = new ArrayDeque<>();
            for (int i = 0; i < 15; i += 2) {
                border.add(new int[] {i, 0});
                border.add(new int[] {i, 14});
            }
            Map<String, Boolean> taken = new HashMap<>();
            JsonNode end = null;
            for (int turn = 0; turn < 20 && end == null; turn++) {
                int[] cell = border.poll();
                while (taken.containsKey(cell[0] + "," + cell[1])) {
                    cell = border.poll();
                }
                a.ok("MOVE", Map.of("matchId", matchId, "x", cell[0], "y", cell[1]));
                JsonNode botMove = a.awaitType("MOVE_APPLIED", d -> d.path("mark").asText().equals("O")).path("data");
                taken.put(botMove.path("x").asInt() + "," + botMove.path("y").asInt(), true);
                if (botMove.path("nextTurn").isNull()) {
                    end = a.awaitType("MATCH_END").path("data");
                }
            }
            assertThat(end).as("the bot should have won within 20 moves").isNotNull();
            assertThat(end.path("result").asText()).isEqualTo("O_WIN");
            assertThat(end.path("reason").asText()).isEqualTo("FIVE_IN_ROW");
            assertThat(end.path("winnerId").asLong()).isEqualTo(-1);
        }
    }

    // ------------------------------------------------------------------ helpers

    private TestClient tcpLoggedIn(String username) throws Exception {
        TestClient client = TestClient.tcp(tcpServer.getLocalPort());
        client.ok("REGISTER", Map.of("username", username, "password", "secret1"));
        client.ok("LOGIN", Map.of("username", username, "password", "secret1"));
        return client;
    }

    /** MATCH_END is sent a few ms before the match is saved (see CLAUDE.md, known limitations). */
    private static void awaitStored(TestClient client, String matchId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (client.request("GET_MATCH", Map.of("matchId", matchId)).path("type").asText().equals("OK")) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("match " + matchId + " was not stored");
    }

    private static long id(TestClient client) {
        return client.ok("GET_PROFILE", Map.of()).path("player").path("id").asLong();
    }

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
