package com.caro.server.player;

import java.time.Instant;
import java.util.List;

import com.caro.server.game.Mark;

/** A stored match with all its moves, enough for a client to replay it. winLine is null unless FIVE_IN_ROW. */
public record MatchDetail(MatchSummary match, List<int[]> winLine, List<Move> moves) {

    public record Move(int moveNo, Mark mark, int x, int y, Instant playedAt) {
    }
}
