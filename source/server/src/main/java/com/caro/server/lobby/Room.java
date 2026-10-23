package com.caro.server.lobby;

import java.util.ArrayList;
import java.util.List;

import com.caro.server.match.GameMatch;
import com.caro.server.user.PlayerInfo;

/** Mutable room state. Not thread-safe: only touched while holding the lobby lock. */
final class Room {

    static final int CAPACITY = 2;

    private final String id;
    private final String name;
    private final List<PlayerInfo> players = new ArrayList<>(CAPACITY); // index 0 is the host
    private GameMatch match; // latest match played in this room, may be finished

    Room(String id, String name, PlayerInfo host) {
        this.id = id;
        this.name = name;
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

    boolean isEmpty() {
        return players.isEmpty();
    }

    void addPlayer(PlayerInfo player) {
        players.add(player);
    }

    void removePlayer(long userId) {
        players.removeIf(p -> p.id() == userId);
    }

    /** Replaces the stored info of a player (e.g. after their rating changed). */
    void updatePlayer(PlayerInfo player) {
        players.replaceAll(p -> p.id() == player.id() ? player : p);
    }

    GameMatch match() {
        return match;
    }

    void setMatch(GameMatch match) {
        this.match = match;
    }

    /** Derived from the players and the match, so it can never get out of sync. */
    RoomStatus status() {
        if (players.size() < CAPACITY) {
            return RoomStatus.WAITING;
        }
        return match != null && !match.isFinished() ? RoomStatus.PLAYING : RoomStatus.FINISHED;
    }

    RoomInfo info() {
        return new RoomInfo(id, name, status(), host().id(), players(), match == null ? null : match.id());
    }
}
