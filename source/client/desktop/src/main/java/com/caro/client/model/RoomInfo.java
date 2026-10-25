package com.caro.client.model;

import java.util.List;

/**
 * status: WAITING | PLAYING | FINISHED. players[0] is the host; the bot appears there with id -1.
 * botLevel: EASY | HARD for a room against the bot, otherwise null.
 */
public record RoomInfo(String id, String name, String status, long hostId, List<PlayerInfo> players, String matchId,
                       List<Long> rematchRequests, boolean blockTwoEnds, String botLevel,
                       List<PlayerInfo> spectators) {

    public static final long BOT_ID = -1;

    public boolean isWaiting() {
        return "WAITING".equals(status);
    }

    public boolean isFinished() {
        return "FINISHED".equals(status);
    }

    public PlayerInfo host() {
        return players.isEmpty() ? null : players.get(0);
    }

    public boolean rematchRequestedBy(long userId) {
        return rematchRequests != null && rematchRequests.contains(userId);
    }

    public List<PlayerInfo> spectatorList() {
        return spectators == null ? List.of() : spectators;
    }

    public boolean hasSpectator(long userId) {
        return spectatorList().stream().anyMatch(p -> p.id() == userId);
    }
}
