package com.caro.client.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A stored match with all its moves, for replay. */
public record MatchDetail(Summary match, List<int[]> winLine, List<Move> moves) {

    public record Summary(String matchId, String roomId, PlayerInfo playerX, PlayerInfo playerO, String result,
                          String reason, Long winnerId, boolean blockTwoEnds, Map<String, Integer> ratingChange,
                          int totalMoves, Instant startedAt, Instant endedAt) {
    }

    public record Move(int moveNo, String mark, int x, int y, Instant playedAt) {
    }
}
