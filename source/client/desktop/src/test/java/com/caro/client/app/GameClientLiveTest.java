package com.caro.client.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.caro.client.model.HistoryItem;
import com.caro.client.model.MatchDetail;
import com.caro.client.model.Page;
import com.caro.client.model.UserInfo;

/**
 * Two real clients against a running server. Skipped unless CARO_LIVE_PORT is set, e.g.
 * {@code cd source && docker compose up -d} then {@code CARO_LIVE_PORT=9090 ./mvnw test}.
 */
@EnabledIfEnvironmentVariable(named = "CARO_LIVE_PORT", matches = "\\d+")
class GameClientLiveTest {

    private static final String HOST = System.getenv().getOrDefault("CARO_LIVE_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("CARO_LIVE_PORT", "9090"));

    /** One client with its own "UI thread", like the app has the Swing thread. */
    private static final class Player implements AutoCloseable {
        final ExecutorService ui = Executors.newSingleThreadExecutor();
        final GameClient client = new GameClient(ui);
        final List<String> notices = new CopyOnWriteArrayList<>();
        final String username = "lv" + UUID.randomUUID().toString().substring(0, 8);

        Player() {
            client.addListener(new ClientListener() {
                @Override
                public void notice(String text) {
                    notices.add(text);
                }
            });
        }

        <T> T call(Supplier<CompletableFuture<T>> action) throws Exception {
            CompletableFuture<T> future = ui.submit(action::get).get(5, TimeUnit.SECONDS);
            return future.get(10, TimeUnit.SECONDS);
        }

        <T> T read(Callable<T> query) throws Exception {
            return ui.submit(query).get(5, TimeUnit.SECONDS);
        }

        void await(String what, Callable<Boolean> condition) throws Exception {
            long deadline = System.currentTimeMillis() + 15_000;
            while (!read(condition)) {
                if (System.currentTimeMillis() > deadline) {
                    throw new AssertionError(username + ": timed out waiting for " + what);
                }
                Thread.sleep(50);
            }
        }

        UserInfo register() throws Exception {
            return call(() -> client.register(HOST, PORT, username, "secret1", null));
        }

        @Override
        public void close() throws Exception {
            ui.submit(client::shutdown).get(5, TimeUnit.SECONDS);
            ui.shutdownNow();
        }
    }

    private final List<Player> players = new CopyOnWriteArrayList<>();

    private Player player() {
        Player p = new Player();
        players.add(p);
        return p;
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Player p : players) {
            p.close();
        }
    }

    @Test
    void fullMatchThenRematchThenHistoryAndReplay() throws Exception {
        Player a = player();
        Player b = player();
        a.register();
        b.register();

        String roomId = a.call(() -> a.client.createRoom("live test")).id();
        b.call(() -> b.client.joinRoom(roomId));
        a.await("match start", () -> a.client.match() != null && a.client.match().isInProgress());
        b.await("match start", () -> b.client.match() != null);
        assertEquals("X", a.read(() -> a.client.match().myMark()));

        for (int i = 0; i < 4; i++) {
            int x = i;
            a.call(() -> a.client.move(x, 7));
            b.await("my turn", () -> b.client.match().isMyTurn());
            b.call(() -> b.client.move(x, 8));
            a.await("my turn", () -> a.client.match().isMyTurn());
        }
        a.call(() -> a.client.move(4, 7));
        a.await("match end", () -> a.client.match().isFinished());
        b.await("match end", () -> b.client.match().isFinished());
        assertEquals("X_WIN", a.read(() -> a.client.match().result().result()));
        assertEquals(5, a.read(() -> a.client.match().winLine().size()));
        assertEquals(1016, a.read(() -> a.client.me().rating()));
        assertEquals(984, b.read(() -> b.client.me().rating()));
        String firstMatch = a.read(() -> a.client.match().matchId());

        // rematch: both ask, marks are swapped
        a.call(() -> a.client.rematch());
        b.await("rematch request visible", () -> b.client.room().rematchRequestedBy(a.client.me().id()));
        b.call(() -> b.client.rematch());
        a.await("second match", () -> !a.client.match().matchId().equals(firstMatch));
        assertEquals("O", a.read(() -> a.client.match().myMark()));

        Page<HistoryItem> history = a.call(() -> a.client.history(null, 0, 10));
        assertEquals(1, history.totalItems());
        assertEquals("WIN", history.items().get(0).outcome());
        MatchDetail detail = a.call(() -> a.client.matchDetail(firstMatch));
        assertEquals(9, detail.moves().size());
        assertEquals(5, detail.winLine().size());
        assertTrue(a.call(() -> a.client.leaderboard(0, 50)).totalItems() >= 2);
    }

    @Test
    void reconnectsOnItsOwnAndTheMatchContinues() throws Exception {
        Player a = player();
        Player b = player();
        a.register();
        b.register();
        String roomId = a.call(() -> a.client.createRoom(null)).id();
        b.call(() -> b.client.joinRoom(roomId));
        a.await("match start", () -> a.client.match() != null && a.client.match().isInProgress());
        a.call(() -> a.client.move(7, 7));
        String matchId = a.read(() -> a.client.match().matchId());

        a.ui.submit(a.client::simulateNetworkFailure).get(5, TimeUnit.SECONDS);
        b.await("opponent paused", () -> b.client.match().isPaused());
        a.await("reconnected", () -> a.notices.contains("Đã kết nối lại"));
        b.await("opponent back", () -> b.client.match().isInProgress());

        a.await("match state resent", () -> a.client.match() != null && a.client.match().moveCount() == 1);
        assertEquals(matchId, a.read(() -> a.client.match().matchId()));
        assertEquals('X', a.read(() -> a.client.match().cell(7, 7)));
        b.call(() -> b.client.move(8, 8));
        a.await("move after reconnect", () -> a.client.match().isMyTurn());
        assertNotEquals(null, a.read(() -> a.client.room()));
    }

    @Test
    void quickMatchAndInvitation() throws Exception {
        Player a = player();
        Player b = player();
        Player c = player();
        a.register();
        b.register();
        c.register();

        a.call(() -> a.client.quickMatch());
        b.call(() -> b.client.quickMatch());
        a.await("matched", () -> a.client.match() != null && a.client.match().isInProgress());
        b.await("matched", () -> b.client.match() != null);
        assertTrue(!a.read(() -> a.client.isInQueue()));

        List<String> invites = new CopyOnWriteArrayList<>();
        c.ui.submit(() -> c.client.addListener(new ClientListener() {
            @Override
            public void invited(com.caro.client.model.Invitation invitation) {
                invites.add(invitation.inviteId());
            }
        })).get(5, TimeUnit.SECONDS);
        Player d = player();
        d.register();
        long cId = c.read(() -> c.client.me().id());
        d.call(() -> d.client.invite(cId));
        c.await("invited", () -> !invites.isEmpty());
        c.call(() -> c.client.acceptInvite(invites.get(0)));
        d.await("invite match", () -> d.client.match() != null && d.client.match().isInProgress());
        assertEquals("X", d.read(() -> d.client.match().myMark()));
    }

    @Test
    void spectatorWatchesAndChatsThenPlaysTheBot() throws Exception {
        Player a = player();
        Player b = player();
        Player c = player();
        a.register();
        b.register();
        c.register();

        String roomId = a.call(() -> a.client.createRoom("luật chặn hai đầu", true)).id();
        assertTrue(a.read(() -> a.client.room().blockTwoEnds()));
        b.call(() -> b.client.joinRoom(roomId));
        a.await("match start", () -> a.client.match() != null && a.client.match().isInProgress());
        a.call(() -> a.client.move(7, 7));

        c.call(() -> c.client.spectate(roomId));
        c.await("spectator sees the board", () -> c.client.match() != null && c.client.match().moveCount() == 1);
        assertTrue(c.read(() -> c.client.isSpectating()));
        assertTrue(c.read(() -> c.client.match().isSpectating() && !c.client.match().isMyTurn()));
        assertTrue(c.read(() -> c.client.match().blockTwoEnds()));

        b.call(() -> b.client.move(8, 8));
        c.await("spectator gets the move", () -> c.client.match().moveCount() == 2);

        c.call(() -> c.client.sendChat("Hay quá!"));
        a.await("chat to players", () -> a.client.chat().size() == 1);
        assertEquals("Hay quá!", a.read(() -> a.client.chat().get(0).text()));
        assertTrue(a.read(() -> a.client.chat().get(0).spectator()));

        c.call(() -> c.client.leaveRoom());
        assertEquals(null, c.read(() -> c.client.room()));
        assertTrue(c.read(() -> c.client.chat().isEmpty()));

        // against the bot: unrated, the bot answers my move
        c.call(() -> c.client.playBot("HARD", false));
        c.await("bot match", () -> c.client.match() != null && c.client.match().isMyTurn());
        assertTrue(!c.read(() -> c.client.match().isRated()));
        c.call(() -> c.client.move(7, 7));
        c.await("bot replied", () -> c.client.match().moveCount() == 2 && c.client.match().isMyTurn());
        c.call(() -> c.client.resign());
        c.await("bot match over", () -> c.client.match().isFinished());
        assertEquals(1000, c.read(() -> c.client.me().rating()));
    }
}
