package com.caro.client.model;

import java.time.Instant;

/** Public profile. rank is null for players that are not ranked (admin, locked). */
public record Profile(PlayerInfo player, boolean online, Integer rank, Stats stats, Instant joinedAt) {

    public record Stats(long played, long wins, long losses, long draws, double winRate) {
    }
}
