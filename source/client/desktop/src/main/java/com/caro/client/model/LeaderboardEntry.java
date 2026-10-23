package com.caro.client.model;

/** One line of the leaderboard. */
public record LeaderboardEntry(int rank, PlayerInfo player, boolean online) {
}
