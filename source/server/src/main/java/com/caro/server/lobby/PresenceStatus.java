package com.caro.server.lobby;

/** What an online player is doing, shown in the online list. Only IDLE and IN_QUEUE players can be invited. */
public enum PresenceStatus {
    IDLE,
    IN_QUEUE,
    /** In a room that is WAITING or FINISHED. */
    IN_ROOM,
    PLAYING
}
