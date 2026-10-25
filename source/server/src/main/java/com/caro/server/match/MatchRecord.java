package com.caro.server.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** A finished match as stored in the database (table matches + match_moves). */
@Entity
@Table(name = "matches")
public class MatchRecord {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false, length = 20)
    private String roomId;

    @Column(name = "player_x_id", nullable = false)
    private long playerXId;

    @Column(name = "player_o_id", nullable = false)
    private long playerOId;

    @Column(name = "winner_id")
    private Long winnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MatchResultType result;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", nullable = false, length = 20)
    private EndReason endReason;

    @Column(name = "rating_change_x", nullable = false)
    private int ratingChangeX;

    @Column(name = "rating_change_o", nullable = false)
    private int ratingChangeO;

    @Column(name = "block_two_ends", nullable = false)
    private boolean blockTwoEnds;

    @Column(name = "total_moves", nullable = false)
    private int totalMoves;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @ElementCollection
    @CollectionTable(name = "match_moves", joinColumns = @JoinColumn(name = "match_id"))
    @OrderBy("moveNo")
    private List<MoveRecord> moves = new ArrayList<>();

    protected MatchRecord() {
        // for JPA
    }

    public static MatchRecord of(GameMatch match) {
        MatchResult r = match.result();
        MatchRecord rec = new MatchRecord();
        rec.id = match.id();
        rec.roomId = match.roomId();
        rec.playerXId = match.playerX().id();
        rec.playerOId = match.playerO().id();
        rec.winnerId = r.winnerId();
        rec.result = r.result();
        rec.endReason = r.reason();
        rec.ratingChangeX = r.ratingChangeX();
        rec.ratingChangeO = r.ratingChangeO();
        rec.blockTwoEnds = match.options().blockTwoEnds();
        rec.startedAt = match.startedAt();
        rec.endedAt = match.endedAt();
        for (PlayedMove m : match.moves()) {
            rec.moves.add(new MoveRecord(m.moveNo(), m.playerId(), m.x(), m.y(), m.playedAt()));
        }
        rec.totalMoves = rec.moves.size();
        return rec;
    }

    public UUID getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public long getPlayerXId() {
        return playerXId;
    }

    public long getPlayerOId() {
        return playerOId;
    }

    public Long getWinnerId() {
        return winnerId;
    }

    public MatchResultType getResult() {
        return result;
    }

    public EndReason getEndReason() {
        return endReason;
    }

    public int getRatingChangeX() {
        return ratingChangeX;
    }

    public int getRatingChangeO() {
        return ratingChangeO;
    }

    public boolean isBlockTwoEnds() {
        return blockTwoEnds;
    }

    public int getTotalMoves() {
        return totalMoves;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public List<MoveRecord> getMoves() {
        return moves;
    }
}
