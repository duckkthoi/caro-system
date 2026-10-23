package com.caro.server.lobby;

public enum RoomStatus {
    /** One player, waiting for an opponent. */
    WAITING,
    /** Two players, match in progress. */
    PLAYING,
    /** Two players, the match just ended. */
    FINISHED
}
