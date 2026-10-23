package com.caro.server.player;

import com.caro.server.user.PlayerInfo;

/** Players with the same rating share the same rank (1, 2, 2, 4...). */
public record LeaderboardEntry(int rank, PlayerInfo player, boolean online) {
}
