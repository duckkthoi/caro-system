package com.caro.client.model;

import java.util.List;

/** Full snapshot of a match (MATCH_START, MATCH_STATE). yourMark is null when I am only watching. */
public record MatchState(String matchId, String roomId, int boardSize, boolean blockTwoEnds, boolean rated,
                         PlayerInfo playerX, PlayerInfo playerO, String yourMark, String status, String turn,
                         int moveCount, List<String> board, LastMove lastMove, Long turnDeadline, long turnTimeLeftMs,
                         MatchResult result) {

    public record LastMove(int x, int y, String mark) {
    }
}
