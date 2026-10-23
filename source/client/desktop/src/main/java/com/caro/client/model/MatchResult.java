package com.caro.client.model;

import java.util.List;
import java.util.Map;

/** MATCH_END event. result: X_WIN | O_WIN | DRAW | ABORTED. ratingChange has the keys X and O. */
public record MatchResult(String matchId, String result, String reason, Long winnerId, List<int[]> winLine,
                          Map<String, Integer> ratingChange) {

    public int ratingChangeOf(String mark) {
        Integer change = ratingChange == null ? null : ratingChange.get(mark);
        return change == null ? 0 : change;
    }
}
