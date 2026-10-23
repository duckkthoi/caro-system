package com.caro.client.app;

import java.util.List;

import com.caro.client.model.MatchResult;
import com.caro.client.model.MatchState;
import com.caro.client.model.MoveApplied;
import com.caro.client.model.PlayerInfo;

/**
 * The client's copy of the current match. It is only a mirror: every change comes from a server message
 * (MATCH_START/MATCH_STATE replace it, MOVE_APPLIED/MATCH_END/OPPONENT_* update it). The client never
 * decides a move or a result by itself. Only touched on the Swing thread.
 */
public final class MatchView implements BoardModel {

    private final String matchId;
    private final String roomId;
    private final int size;
    private final char[][] cells; // [y][x]: '.', 'X' or 'O'
    private final PlayerInfo playerX;
    private final PlayerInfo playerO;
    private final String myMark;
    private String status;          // IN_PROGRESS | PAUSED | FINISHED
    private String turn;
    private Long turnDeadline;      // server epoch ms, null unless IN_PROGRESS
    private long pausedTimeLeftMs;  // turn time left while PAUSED
    private Long graceDeadline;     // while the opponent is disconnected
    private int moveCount;
    private int[] lastMove;
    private MatchResult result;

    private MatchView(MatchState s) {
        this.matchId = s.matchId();
        this.roomId = s.roomId();
        this.size = s.boardSize();
        this.cells = new char[size][];
        for (int y = 0; y < size; y++) {
            cells[y] = s.board().get(y).toCharArray();
        }
        this.playerX = s.playerX();
        this.playerO = s.playerO();
        this.myMark = s.yourMark();
        this.status = s.status();
        this.turn = s.turn();
        this.turnDeadline = s.turnDeadline();
        this.pausedTimeLeftMs = s.turnTimeLeftMs();
        this.moveCount = s.moveCount();
        this.lastMove = s.lastMove() == null ? null : new int[] {s.lastMove().x(), s.lastMove().y()};
        this.result = s.result();
    }

    public static MatchView of(MatchState state) {
        return new MatchView(state);
    }

    void apply(MoveApplied m) {
        cells[m.y()][m.x()] = m.mark().charAt(0);
        lastMove = new int[] {m.x(), m.y()};
        moveCount = m.moveNo();
        turn = m.nextTurn();
        turnDeadline = m.turnDeadline();
    }

    void finish(MatchResult r) {
        status = "FINISHED";
        result = r;
        turn = null;
        turnDeadline = null;
        graceDeadline = null;
    }

    void opponentDisconnected(long graceDeadline, long serverNow) {
        if (turnDeadline != null) {
            pausedTimeLeftMs = Math.max(0, turnDeadline - serverNow);
        }
        status = "PAUSED";
        turnDeadline = null;
        this.graceDeadline = graceDeadline;
    }

    void opponentReconnected(long turnDeadline) {
        status = "IN_PROGRESS";
        this.turnDeadline = turnDeadline;
        graceDeadline = null;
    }

    // ------------------------------------------------------------------ queries

    public String matchId() {
        return matchId;
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

    public PlayerInfo opponent() {
        return "X".equals(myMark) ? playerO : playerX;
    }

    public String myMark() {
        return myMark;
    }

    public String status() {
        return status;
    }

    public boolean isInProgress() {
        return "IN_PROGRESS".equals(status);
    }

    public boolean isPaused() {
        return "PAUSED".equals(status);
    }

    public boolean isFinished() {
        return "FINISHED".equals(status);
    }

    public String turn() {
        return turn;
    }

    public boolean isMyTurn() {
        return isInProgress() && myMark.equals(turn);
    }

    public int moveCount() {
        return moveCount;
    }

    public MatchResult result() {
        return result;
    }

    /** Milliseconds left in the current turn, using the server clock. */
    public long turnTimeLeftMs(long serverNow) {
        if (turnDeadline != null) {
            return Math.max(0, turnDeadline - serverNow);
        }
        return isPaused() ? pausedTimeLeftMs : 0;
    }

    public Long graceDeadline() {
        return graceDeadline;
    }

    /** True if the cell is empty and it is my turn, i.e. clicking it makes sense. */
    public boolean canPlay(int x, int y) {
        return isMyTurn() && x >= 0 && y >= 0 && x < size && y < size && cells[y][x] == '.';
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public char cell(int x, int y) {
        return cells[y][x];
    }

    @Override
    public int[] lastMove() {
        return lastMove;
    }

    @Override
    public List<int[]> winLine() {
        return result == null || result.winLine() == null ? List.of() : result.winLine();
    }
}
