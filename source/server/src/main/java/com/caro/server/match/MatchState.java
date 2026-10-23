package com.caro.server.match;

import java.util.List;
import java.util.UUID;

import com.caro.server.game.Mark;
import com.caro.server.user.PlayerInfo;

/** Full snapshot of a match for one recipient (MATCH_START, MATCH_STATE, GET_STATE). */
public record MatchState(
        UUID matchId,
        String roomId,
        int boardSize,
        PlayerInfo playerX,
        PlayerInfo playerO,
        Mark yourMark,
        MatchStatus status,
        Mark turn,
        int moveCount,
        List<String> board,
        LastMove lastMove,
        Long turnDeadline,
        long turnTimeLeftMs,
        MatchResult result) {

    public record LastMove(int x, int y, Mark mark) {
    }
}
