package com.caro.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.caro.server.common.AppException;
import com.caro.server.common.ErrorCode;
import com.caro.server.config.CaroProperties;
import com.caro.server.game.Mark;
import com.caro.server.net.Notifier;
import com.caro.server.net.ServerMessageType;
import com.caro.server.user.PlayerInfo;

class GameMatchTest {

    private static final long X = 1;
    private static final long O = 2;

    record Event(long userId, ServerMessageType type, Object data) {
    }

    private final List<Event> events = Collections.synchronizedList(new ArrayList<>());
    private final Notifier notifier = (userId, type, data) -> events.add(new Event(userId, type, data));
    private final AtomicInteger endedCount = new AtomicInteger();
    private final CountDownLatch ended = new CountDownLatch(1);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    private GameMatch newMatch(int boardSize, Duration turnTime) {
        var settings = new CaroProperties.Game(boardSize, 5, turnTime, Duration.ofSeconds(30));
        MatchEndListener listener = m -> {
            endedCount.incrementAndGet();
            ended.countDown();
        };
        var runtime = new MatchRuntime(settings, notifier, scheduler, listener, Clock.systemUTC());
        var match = new GameMatch(UUID.randomUUID(), "R1",
                new PlayerInfo(X, "alice", "Alice", 1000), new PlayerInfo(O, "bob", "Bob", 1000), runtime);
        match.start();
        return match;
    }

    private GameMatch newMatch() {
        return newMatch(15, Duration.ofSeconds(30));
    }

    private static void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).code()).isEqualTo(code));
    }

    private long count(long userId, ServerMessageType type) {
        synchronized (events) {
            return events.stream().filter(e -> e.userId() == userId && e.type() == type).count();
        }
    }

    @Test
    void startSendsEachPlayerTheirOwnMark() {
        newMatch();
        synchronized (events) {
            assertThat(events).filteredOn(e -> e.type() == ServerMessageType.MATCH_START).hasSize(2);
            MatchState forO = (MatchState) events.stream()
                    .filter(e -> e.userId() == O && e.type() == ServerMessageType.MATCH_START)
                    .findFirst().orElseThrow().data();
            assertThat(forO.yourMark()).isEqualTo(Mark.O);
            assertThat(forO.turn()).isEqualTo(Mark.X);
        }
    }

    @Test
    void enforcesTurnOrder() {
        GameMatch match = newMatch();
        assertError(() -> match.move(O, 7, 7), ErrorCode.NOT_YOUR_TURN);
        assertThat(match.move(X, 7, 7)).isEqualTo(1);
        assertError(() -> match.move(X, 8, 8), ErrorCode.NOT_YOUR_TURN);
        assertThat(match.move(O, 8, 8)).isEqualTo(2);
    }

    @Test
    void rejectsInvalidCells() {
        GameMatch match = newMatch();
        assertError(() -> match.move(X, 15, 0), ErrorCode.INVALID_POSITION);
        assertError(() -> match.move(X, -1, 3), ErrorCode.INVALID_POSITION);
        match.move(X, 7, 7);
        assertError(() -> match.move(O, 7, 7), ErrorCode.CELL_OCCUPIED);
        assertError(() -> match.move(99, 0, 0), ErrorCode.MATCH_NOT_FOUND);
    }

    @Test
    void fiveInARowWinsAndTheMatchEndsExactlyOnce() {
        GameMatch match = newMatch();
        long generation = 0;
        for (int i = 0; i < 4; i++) {
            match.move(X, i, 0);
            match.move(O, i, 1);
            generation = match.turnTimerGeneration();
        }
        match.move(X, 4, 0);

        MatchResult result = match.result();
        assertThat(result.result()).isEqualTo(MatchResultType.X_WIN);
        assertThat(result.reason()).isEqualTo(EndReason.FIVE_IN_ROW);
        assertThat(result.winnerId()).isEqualTo(X);
        assertThat(result.winLine()).hasSize(5);
        assertThat(result.ratingChangeX()).isEqualTo(16);
        assertThat(result.ratingChangeO()).isEqualTo(-16);

        // nothing can end it a second time
        assertError(() -> match.move(O, 9, 9), ErrorCode.MATCH_NOT_IN_PROGRESS);
        assertError(() -> match.resign(O), ErrorCode.MATCH_NOT_IN_PROGRESS);
        match.onTurnTimeout(generation);
        match.playerDisconnected(O);
        assertThat(endedCount).hasValue(1);
        assertThat(count(X, ServerMessageType.MATCH_END)).isEqualTo(1);
        assertThat(count(O, ServerMessageType.MATCH_END)).isEqualTo(1);
        assertThat(match.moves()).hasSize(9);
    }

    @Test
    void fullBoardIsADraw() {
        GameMatch match = newMatch(5, Duration.ofSeconds(30));
        // final position has no 5 in a row:  XXOOX / OOXXO / XXOOX / OOXXO / XXOOX
        String[] rows = {"XXOOX", "OOXXO", "XXOOX", "OOXXO", "XXOOX"};
        List<int[]> xs = new ArrayList<>();
        List<int[]> os = new ArrayList<>();
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                (rows[y].charAt(x) == 'X' ? xs : os).add(new int[] {x, y});
            }
        }
        for (int i = 0; i < xs.size(); i++) {
            match.move(X, xs.get(i)[0], xs.get(i)[1]);
            if (i < os.size()) {
                match.move(O, os.get(i)[0], os.get(i)[1]);
            }
        }
        assertThat(match.result().result()).isEqualTo(MatchResultType.DRAW);
        assertThat(match.result().reason()).isEqualTo(EndReason.BOARD_FULL);
        assertThat(match.result().winnerId()).isNull();
    }

    @Test
    void resignGivesTheWinToTheOpponent() {
        GameMatch match = newMatch();
        match.resign(X);
        assertThat(match.result().result()).isEqualTo(MatchResultType.O_WIN);
        assertThat(match.result().reason()).isEqualTo(EndReason.RESIGN);
    }

    @Test
    void turnTimeoutLosesForThePlayerOnTurn() throws InterruptedException {
        GameMatch match = newMatch(15, Duration.ofMillis(150));
        match.move(X, 7, 7);
        assertThat(ended.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(match.result().result()).isEqualTo(MatchResultType.X_WIN); // O was on turn
        assertThat(match.result().reason()).isEqualTo(EndReason.TIMEOUT);
    }

    @Test
    void staleTimeoutIsIgnored() {
        GameMatch match = newMatch();
        long generation = match.turnTimerGeneration();
        match.move(X, 7, 7); // restarts the clock
        match.onTurnTimeout(generation);
        assertThat(match.isFinished()).isFalse();
    }

    @Test
    void disconnectPausesAndReconnectResumes() {
        GameMatch match = newMatch();
        match.move(X, 7, 7);
        match.playerDisconnected(X);

        assertThat(match.status()).isEqualTo(MatchStatus.PAUSED);
        assertThat(count(O, ServerMessageType.OPPONENT_DISCONNECTED)).isEqualTo(1);
        assertError(() -> match.move(O, 8, 8), ErrorCode.MATCH_NOT_IN_PROGRESS);

        match.playerReconnected(X);
        assertThat(match.status()).isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(count(O, ServerMessageType.OPPONENT_RECONNECTED)).isEqualTo(1);
        assertThat(count(X, ServerMessageType.MATCH_STATE)).isEqualTo(1);
        assertThat(match.snapshotFor(X).board().get(7)).isEqualTo(".......X.......");
        assertThat(match.move(O, 8, 8)).isEqualTo(2);
    }

    @Test
    void graceExpiryLosesForTheDisconnectedPlayer() {
        GameMatch match = newMatch();
        match.playerDisconnected(O);
        match.onGraceExpired(O);
        assertThat(match.result().result()).isEqualTo(MatchResultType.X_WIN);
        assertThat(match.result().reason()).isEqualTo(EndReason.DISCONNECT);
    }

    @Test
    void bothDisconnectedAbortsWithoutRatingChange() {
        GameMatch match = newMatch();
        match.playerDisconnected(X);
        match.playerDisconnected(O);
        assertThat(match.result().result()).isEqualTo(MatchResultType.ABORTED);
        assertThat(match.result().reason()).isEqualTo(EndReason.BOTH_DISCONNECTED);
        assertThat(match.result().ratingChangeX()).isZero();
        assertThat(endedCount).hasValue(1);
    }
}
