package com.caro.server.player;

import java.time.Instant;

import com.caro.server.user.PlayerInfo;

/** Public profile of a player. rank is null for accounts that are not on the leaderboard (admin, locked). */
public record Profile(PlayerInfo player, boolean online, Integer rank, PlayerStats stats, Instant joinedAt) {
}
