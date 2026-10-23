package com.caro.server.match;

public enum MatchStatus {
    IN_PROGRESS,
    /** One player is disconnected; the turn clock is stopped until they come back or the grace period ends. */
    PAUSED,
    FINISHED
}
