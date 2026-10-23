package com.caro.server.lobby;

import java.util.concurrent.ScheduledFuture;

import com.caro.server.user.PlayerInfo;

/** A pending invitation. Not thread-safe: only touched while holding the lobby lock. */
final class Invitation {

    private final String id;
    private final PlayerInfo from;
    private final long toId;
    private final long expiresAt;
    private ScheduledFuture<?> expiryTimer;

    Invitation(String id, PlayerInfo from, long toId, long expiresAt) {
        this.id = id;
        this.from = from;
        this.toId = toId;
        this.expiresAt = expiresAt;
    }

    String id() {
        return id;
    }

    PlayerInfo from() {
        return from;
    }

    long fromId() {
        return from.id();
    }

    long toId() {
        return toId;
    }

    long expiresAt() {
        return expiresAt;
    }

    boolean involves(long userId) {
        return from.id() == userId || toId == userId;
    }

    void setExpiryTimer(ScheduledFuture<?> timer) {
        this.expiryTimer = timer;
    }

    void cancelTimer() {
        if (expiryTimer != null) {
            expiryTimer.cancel(false);
        }
    }
}
