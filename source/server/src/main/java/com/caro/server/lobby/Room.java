package com.caro.server.lobby;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.caro.server.bot.BotLevel;
import com.caro.server.bot.BotPlayer;
import com.caro.server.match.GameMatch;
import com.caro.server.match.MatchOptions;
import com.caro.server.user.PlayerInfo;

/** Mutable room state. Not thread-safe: only touched while holding the lobby lock. */
final class Room {

    static final int CAPACITY = 2;
    static final int MAX_SPECTATORS = 10;

    private final String id;
    private final String name;
    private final boolean blockTwoEnds;
    private final BotLevel botLevel; // null unless this is a room against the bot
    private final List<PlayerInfo> players = new ArrayList<>(CAPACITY); // index 0 is the host
    private final Map<Long, PlayerInfo> spectators = new LinkedHashMap<>();
    private final Set<Long> rematchRequests = new LinkedHashSet<>();
    private GameMatch match; // latest match played in this room, may be finished

    Room(String id, String name, PlayerInfo host, boolean blockTwoEnds, BotLevel botLevel) {
        this.id = id;
        this.name = name;
        this.blockTwoEnds = blockTwoEnds;
        this.botLevel = botLevel;
        this.players.add(host);
    }

    String id() {
        return id;
    }

    PlayerInfo host() {
        return players.get(0);
    }

    List<PlayerInfo> players() {
        return List.copyOf(players);
    }

    BotLevel botLevel() {
        return botLevel;
    }

    MatchOptions options() {
        return new MatchOptions(blockTwoEnds, botLevel == null);
    }

    /** True when no human player is left (the bot alone does not keep a room alive). */
    boolean isEmpty() {
        return players.stream().allMatch(p -> BotPlayer.isBot(p.id()));
    }

    void addPlayer(PlayerInfo player) {
        players.add(player);
    }

    void removePlayer(long userId) {
        players.removeIf(p -> p.id() == userId);
        rematchRequests.clear();
    }

    /** Replaces the stored info of a player (e.g. after their rating changed). */
    void updatePlayer(PlayerInfo player) {
        players.replaceAll(p -> p.id() == player.id() ? player : p);
    }

    // ---- spectators (docs/rules.md, section 10)

    boolean isFullOfSpectators() {
        return spectators.size() >= MAX_SPECTATORS;
    }

    void addSpectator(PlayerInfo spectator) {
        spectators.put(spectator.id(), spectator);
    }

    void removeSpectator(long userId) {
        spectators.remove(userId);
    }

    List<Long> spectatorIds() {
        return List.copyOf(spectators.keySet());
    }

    /** Everyone who gets the room's events: human players, then spectators. */
    List<Long> memberIds() {
        List<Long> ids = new ArrayList<>();
        for (PlayerInfo p : players) {
            if (!BotPlayer.isBot(p.id())) {
                ids.add(p.id());
            }
        }
        ids.addAll(spectators.keySet());
        return ids;
    }

    GameMatch match() {
        return match;
    }

    void setMatch(GameMatch match) {
        this.match = match;
        rematchRequests.clear();
    }

    /** @return true once both players have asked */
    boolean requestRematch(long userId) {
        rematchRequests.add(userId);
        return rematchRequests.size() == CAPACITY;
    }

    /** Derived from the players and the match, so it can never get out of sync. */
    RoomStatus status() {
        if (players.size() < CAPACITY) {
            return RoomStatus.WAITING;
        }
        return match != null && !match.isFinished() ? RoomStatus.PLAYING : RoomStatus.FINISHED;
    }

    RoomInfo info() {
        return new RoomInfo(id, name, status(), host().id(), players(), match == null ? null : match.id(),
                List.copyOf(rematchRequests), blockTwoEnds, botLevel, List.copyOf(spectators.values()));
    }
}
