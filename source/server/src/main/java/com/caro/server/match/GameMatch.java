package com.caro.server.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.caro.server.common.AppException;
import com.caro.server.common.ErrorCode;
import com.caro.server.game.Board;
import com.caro.server.game.Elo;
import com.caro.server.game.Mark;
import com.caro.server.game.RuleEngine;
import com.caro.server.net.ServerMessageType;
import com.caro.server.user.PlayerInfo;

/**
 * One live match between two players. This object is the single source of truth for the match state.
 *
 * <p><b>Concurrency.</b> Moves, resignations, timer callbacks and (dis)connect events can arrive at the same
 * time from different threads. Every state change happens inside {@code synchronized (this)}, so they are
 * applied one at a time. Events are only <i>queued</i> on the sessions inside the lock (no network I/O),
 * which keeps the lock short and guarantees both players see events in the same order.
 *
 * <p><b>End exactly once.</b> {@link #finish} flips the status to FINISHED under the lock and returns false
 * if it already was, so a move and a timeout racing each other can never produce two results.
 * The {@link MatchEndListener} is then called outside the lock.
 *
 * <p><b>Spectators</b> receive the same events as the players. A spectator is added together with their first
 * snapshot under the same lock, so they never miss a move or see one twice.
 */
public class GameMatch {

    private static final Logger log = LoggerFactory.getLogger(GameMatch.class);

    private final UUID id;
    private final String roomId;
    private final PlayerInfo playerX;
    private final PlayerInfo playerO;
    private final MatchOptions options;
    private final MatchRuntime rt;
    private final Board board;

    // ---- guarded by "this" ----
    private final List<PlayedMove> moves = new ArrayList<>();
    private final Set<Long> disconnected = new HashSet<>();
    private final Set<Long> spectators = new LinkedHashSet<>();
    private MatchStatus status = MatchStatus.IN_PROGRESS;
    private Mark turn = Mark.X;
    private long turnDeadline;
    private long turnTimerGeneration; // bumped every time the turn clock restarts
    private long pausedTimeLeftMs;
    private ScheduledFuture<?> turnTimer;
    private ScheduledFuture<?> graceTimer;
    private Instant startedAt;
    private Instant endedAt;
    private MatchResult result;

    public GameMatch(UUID id, String roomId, PlayerInfo playerX, PlayerInfo playerO, MatchRuntime runtime) {
        this(id, roomId, playerX, playerO, MatchOptions.STANDARD, List.of(), runtime);
    }

    /** @param spectators people already watching the room; they get MATCH_START like the players */
    public GameMatch(UUID id, String roomId, PlayerInfo playerX, PlayerInfo playerO, MatchOptions options,
                     Collection<Long> spectators, MatchRuntime runtime) {
        this.id = id;
        this.roomId = roomId;
        this.playerX = playerX;
        this.playerO = playerO;
        this.options = options;
        this.rt = runtime;
        this.board = new Board(runtime.settings().boardSize());
        this.spectators.addAll(spectators);
    }

    // ------------------------------------------------------------------ player actions

    public void start() {
        synchronized (this) {
            startedAt = rt.clock().instant();
            restartTurnTimer(turnTimeMs());
            for (long userId : audience()) {
                rt.notifier().sendToUser(userId, ServerMessageType.MATCH_START, buildSnapshot(userId));
            }
        }
        log.info("Match {} started in room {}: {} (X) vs {} (O)", id, roomId, playerX.username(), playerO.username());
    }

    /** UC14: validate and apply a move (UC16), then check for a win or a draw (UC17). */
    public int move(long userId, int x, int y) {
        int moveNo;
        boolean ended = false;
        synchronized (this) {
            Mark mark = requireMark(userId);
            if (status != MatchStatus.IN_PROGRESS) {
                throw new AppException(ErrorCode.MATCH_NOT_IN_PROGRESS, "Trận đấu không ở trạng thái cho phép đi");
            }
            if (mark != turn) {
                throw new AppException(ErrorCode.NOT_YOUR_TURN, "Chưa đến lượt của bạn");
            }
            if (!board.inBounds(x, y)) {
                throw new AppException(ErrorCode.INVALID_POSITION, "Tọa độ nằm ngoài bàn cờ");
            }
            if (!board.isEmpty(x, y)) {
                throw new AppException(ErrorCode.CELL_OCCUPIED, "Ô đã có quân");
            }

            board.place(x, y, mark);
            moveNo = moves.size() + 1;
            moves.add(new PlayedMove(moveNo, userId, x, y, mark, rt.clock().instant()));

            List<int[]> winLine = RuleEngine.findWinLine(board, x, y, rt.settings().winLength(),
                    options.blockTwoEnds());
            boolean draw = winLine.isEmpty() && board.isFull();
            boolean over = !winLine.isEmpty() || draw;
            if (!over) {
                turn = turn.opposite();
                restartTurnTimer(turnTimeMs());
            }
            broadcast(ServerMessageType.MOVE_APPLIED, new MoveApplied(id, moveNo, x, y, mark,
                    over ? null : turn, over ? null : turnDeadline));
            if (over) {
                ended = draw
                        ? finish(MatchResultType.DRAW, EndReason.BOARD_FULL, List.of())
                        : finish(MatchResultType.winOf(mark), EndReason.FIVE_IN_ROW, winLine);
            }
        }
        fireEndedIf(ended);
        return moveNo;
    }

    /** UC18: the player gives up. */
    public void resign(long userId) {
        boolean ended;
        synchronized (this) {
            Mark mark = requireMark(userId);
            if (status == MatchStatus.FINISHED) {
                throw new AppException(ErrorCode.MATCH_NOT_IN_PROGRESS, "Trận đấu đã kết thúc");
            }
            ended = finish(MatchResultType.winOf(mark.opposite()), EndReason.RESIGN, List.of());
        }
        fireEndedIf(ended);
    }

    /** Leaving the room during a match counts as resigning. Does nothing if the match already ended. */
    public void forfeitIfRunning(long userId) {
        boolean ended = false;
        synchronized (this) {
            Mark mark = markOf(userId);
            if (mark != null && status != MatchStatus.FINISHED) {
                ended = finish(MatchResultType.winOf(mark.opposite()), EndReason.RESIGN, List.of());
            }
        }
        fireEndedIf(ended);
    }

    // ------------------------------------------------------------------ connection events (UC20)

    public void playerDisconnected(long userId) {
        boolean ended = false;
        synchronized (this) {
            if (markOf(userId) == null || status == MatchStatus.FINISHED || !disconnected.add(userId)) {
                return;
            }
            if (disconnected.size() == 2) {
                ended = finish(MatchResultType.ABORTED, EndReason.BOTH_DISCONNECTED, List.of());
            } else {
                // stop the turn clock and remember how much time was left
                pausedTimeLeftMs = Math.max(0, turnDeadline - now());
                cancel(turnTimer);
                turnTimerGeneration++; // invalidates a timeout that is already firing
                status = MatchStatus.PAUSED;
                long graceMs = rt.settings().reconnectGrace().toMillis();
                long graceDeadline = now() + graceMs;
                graceTimer = rt.scheduler().schedule(
                        () -> safely(() -> onGraceExpired(userId)), graceMs, TimeUnit.MILLISECONDS);
                broadcastExcept(userId, ServerMessageType.OPPONENT_DISCONNECTED,
                        event("userId", userId, "graceDeadline", graceDeadline));
                log.info("Match {}: user {} disconnected, waiting {} ms", id, userId, graceMs);
            }
        }
        fireEndedIf(ended);
    }

    /** Called when the user logs in again. Resumes the match and sends them the full state. */
    public void playerReconnected(long userId) {
        synchronized (this) {
            if (markOf(userId) == null) {
                return;
            }
            if (disconnected.remove(userId) && status == MatchStatus.PAUSED) {
                cancel(graceTimer);
                status = MatchStatus.IN_PROGRESS;
                restartTurnTimer(pausedTimeLeftMs);
                broadcastExcept(userId, ServerMessageType.OPPONENT_RECONNECTED,
                        event("userId", userId, "turnDeadline", turnDeadline));
                log.info("Match {}: user {} reconnected", id, userId);
            }
            rt.notifier().sendToUser(userId, ServerMessageType.MATCH_STATE, buildSnapshot(userId));
        }
    }

    // ------------------------------------------------------------------ spectators (docs/rules.md, section 10)

    /** Starts sending the match events to this user, beginning with a full MATCH_STATE. */
    public synchronized void addSpectator(long userId) {
        spectators.add(userId);
        rt.notifier().sendToUser(userId, ServerMessageType.MATCH_STATE, buildSnapshot(userId));
    }

    public synchronized void removeSpectator(long userId) {
        spectators.remove(userId);
    }

    // ------------------------------------------------------------------ timers (package-private for tests)

    /**
     * @param generation the clock generation this timer belongs to. If the clock was restarted meanwhile
     *                   (a move arrived while this timer was already firing), the timeout is stale and ignored.
     */
    void onTurnTimeout(long generation) {
        boolean ended = false;
        synchronized (this) {
            if (status == MatchStatus.IN_PROGRESS && generation == turnTimerGeneration) {
                ended = finish(MatchResultType.winOf(turn.opposite()), EndReason.TIMEOUT, List.of());
            }
        }
        fireEndedIf(ended);
    }

    synchronized long turnTimerGeneration() {
        return turnTimerGeneration;
    }

    void onGraceExpired(long userId) {
        boolean ended = false;
        synchronized (this) {
            if (status == MatchStatus.PAUSED && disconnected.contains(userId)) {
                ended = finish(MatchResultType.winOf(markOf(userId).opposite()), EndReason.DISCONNECT, List.of());
            }
        }
        fireEndedIf(ended);
    }

    // ------------------------------------------------------------------ queries

    public UUID id() {
        return id;
    }

    public String roomId() {
        return roomId;
    }

    public PlayerInfo playerX() {
        return playerX;
    }

    public PlayerInfo playerO() {
        return playerO;
    }

    public MatchOptions options() {
        return options;
    }

    public boolean hasPlayer(long userId) {
        return markOf(userId) != null;
    }

    public synchronized boolean isFinished() {
        return status == MatchStatus.FINISHED;
    }

    public synchronized MatchStatus status() {
        return status;
    }

    public synchronized MatchResult result() {
        return result;
    }

    public synchronized Instant startedAt() {
        return startedAt;
    }

    public synchronized Instant endedAt() {
        return endedAt;
    }

    public synchronized List<PlayedMove> moves() {
        return List.copyOf(moves);
    }

    public synchronized MatchState snapshotFor(long userId) {
        return buildSnapshot(userId);
    }

    // ------------------------------------------------------------------ internals (caller holds the lock)

    private boolean finish(MatchResultType type, EndReason reason, List<int[]> winLine) {
        if (status == MatchStatus.FINISHED) {
            return false;
        }
        status = MatchStatus.FINISHED;
        endedAt = rt.clock().instant();
        cancel(turnTimer);
        cancel(graceTimer);

        int deltaX = !options.rated() ? 0 : switch (type) {
            case X_WIN -> Elo.deltaForX(playerX.rating(), playerO.rating(), 1.0);
            case O_WIN -> Elo.deltaForX(playerX.rating(), playerO.rating(), 0.0);
            case DRAW -> Elo.deltaForX(playerX.rating(), playerO.rating(), 0.5);
            case ABORTED -> 0;
        };
        Long winnerId = switch (type) {
            case X_WIN -> playerX.id();
            case O_WIN -> playerO.id();
            default -> null;
        };
        Map<String, Integer> ratingChange = new LinkedHashMap<>();
        ratingChange.put("X", deltaX);
        ratingChange.put("O", -deltaX);
        result = new MatchResult(id, type, reason, winnerId, winLine, ratingChange);

        broadcast(ServerMessageType.MATCH_END, result);
        log.info("Match {} finished: {} ({}) after {} moves", id, type, reason, moves.size());
        return true;
    }

    private MatchState buildSnapshot(long userId) {
        PlayedMove last = moves.isEmpty() ? null : moves.get(moves.size() - 1);
        long timeLeft = switch (status) {
            case IN_PROGRESS -> Math.max(0, turnDeadline - now());
            case PAUSED -> pausedTimeLeftMs;
            case FINISHED -> 0;
        };
        return new MatchState(id, roomId, board.size(), options.blockTwoEnds(), options.rated(), playerX, playerO,
                markOf(userId), status,
                status == MatchStatus.FINISHED ? null : turn,
                moves.size(),
                board.toRows(),
                last == null ? null : new MatchState.LastMove(last.x(), last.y(), last.mark()),
                status == MatchStatus.IN_PROGRESS ? turnDeadline : null,
                timeLeft,
                result);
    }

    private void restartTurnTimer(long delayMs) {
        cancel(turnTimer);
        turnDeadline = now() + delayMs;
        long generation = ++turnTimerGeneration;
        turnTimer = rt.scheduler().schedule(
                () -> safely(() -> onTurnTimeout(generation)), delayMs, TimeUnit.MILLISECONDS);
    }

    /** Both players, then the spectators. */
    private List<Long> audience() {
        List<Long> ids = new ArrayList<>(2 + spectators.size());
        ids.add(playerX.id());
        ids.add(playerO.id());
        ids.addAll(spectators);
        return ids;
    }

    private void broadcast(ServerMessageType type, Object data) {
        for (long userId : audience()) {
            rt.notifier().sendToUser(userId, type, data);
        }
    }

    /** Everyone but the player the event is about. */
    private void broadcastExcept(long skippedId, ServerMessageType type, Object data) {
        for (long userId : audience()) {
            if (userId != skippedId) {
                rt.notifier().sendToUser(userId, type, data);
            }
        }
    }

    private Map<String, Object> event(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matchId", id);
        data.put(k1, v1);
        data.put(k2, v2);
        return data;
    }

    private Mark requireMark(long userId) {
        Mark mark = markOf(userId);
        if (mark == null) {
            throw new AppException(ErrorCode.MATCH_NOT_FOUND, "Bạn không tham gia trận này");
        }
        return mark;
    }

    private Mark markOf(long userId) {
        if (userId == playerX.id()) {
            return Mark.X;
        }
        if (userId == playerO.id()) {
            return Mark.O;
        }
        return null;
    }

    private long turnTimeMs() {
        return rt.settings().turnTime().toMillis();
    }

    private long now() {
        return rt.clock().millis();
    }

    private void fireEndedIf(boolean ended) {
        if (ended) {
            rt.listener().onMatchEnded(this);
        }
    }

    private static void cancel(ScheduledFuture<?> timer) {
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private static void safely(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("Match timer task failed", e);
        }
    }
}
