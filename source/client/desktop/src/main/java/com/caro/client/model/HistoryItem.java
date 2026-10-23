package com.caro.client.model;

import java.time.Instant;

/** One line of a player's history. outcome: WIN | LOSS | DRAW | ABORTED. */
public record HistoryItem(String matchId, PlayerInfo opponent, String mark, String outcome, String reason,
                          int ratingChange, int totalMoves, Instant startedAt, Instant endedAt) {
}
