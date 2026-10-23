package com.caro.server.lobby;

import java.util.List;
import java.util.UUID;

import com.caro.server.user.PlayerInfo;

/** Room as sent to clients. players[0] is the host. */
public record RoomInfo(String id, String name, RoomStatus status, long hostId, List<PlayerInfo> players, UUID matchId) {
}
