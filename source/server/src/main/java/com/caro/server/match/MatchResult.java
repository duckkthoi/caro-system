package com.caro.server.match;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payload of MATCH_END. ratingChange has the keys "X" and "O". */
public record MatchResult(
        UUID matchId,
        MatchResultType result,
        EndReason reason,
        Long winnerId,
        List<int[]> winLine,
        Map<String, Integer> ratingChange) {

    public int ratingChangeX() {
        return ratingChange.get("X");
    }

    public int ratingChangeO() {
        return ratingChange.get("O");
    }
}
