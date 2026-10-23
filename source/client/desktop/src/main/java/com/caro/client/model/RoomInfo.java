package com.caro.client.model;

import java.util.List;

/** status: WAITING | PLAYING | FINISHED. players[0] is the host. */
public record RoomInfo(String id, String name, String status, long hostId, List<PlayerInfo> players, String matchId,
                       List<Long> rematchRequests) {

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
}
