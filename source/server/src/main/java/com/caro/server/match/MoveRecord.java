package com.caro.server.match;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One row of match_moves. */
@Embeddable
public class MoveRecord {

    @Column(name = "move_no", nullable = false)
    private int moveNo;

    @Column(name = "player_id", nullable = false)
    private long playerId;

    @Column(nullable = false)
    private int x;

    @Column(nullable = false)
    private int y;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    protected MoveRecord() {
        // for JPA
    }

    public MoveRecord(int moveNo, long playerId, int x, int y, Instant playedAt) {
        this.moveNo = moveNo;
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.playedAt = playedAt;
    }

    public int getMoveNo() {
        return moveNo;
    }

    public long getPlayerId() {
        return playerId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }
}
