package com.caro.server.lobby;

/** What an online player is doing, shown in the online list. IDLE, IN_QUEUE and SPECTATING players can be invited. */
public enum PresenceStatus {
    IDLE,
    IN_QUEUE,
    /** In a room that is WAITING or FINISHED. */
    IN_ROOM,
    PLAYING,
    /** Watching someone else's room. */
    SPECTATING
}
