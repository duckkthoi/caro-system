package com.caro.server.lobby;

import java.util.List;
import java.util.UUID;

import com.caro.server.bot.BotLevel;
import com.caro.server.user.PlayerInfo;

/**
 * Room as sent to clients. players[0] is the host. rematchRequests: ids of players who asked for a rematch.
 * botLevel is null unless the second player is the bot.
 */
public record RoomInfo(String id, String name, RoomStatus status, long hostId, List<PlayerInfo> players, UUID matchId,
                       List<Long> rematchRequests, boolean blockTwoEnds, BotLevel botLevel,
                       List<PlayerInfo> spectators) {
}
