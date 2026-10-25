package com.caro.server.player;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.caro.server.match.EndReason;
import com.caro.server.match.MatchRecord;
import com.caro.server.match.MatchResultType;
import com.caro.server.user.PlayerInfo;

/** A stored match, neutral (not seen from either player). ratingChange has the keys "X" and "O". */
public record MatchSummary(
        UUID matchId,
        String roomId,
        PlayerInfo playerX,
        PlayerInfo playerO,
        MatchResultType result,
        EndReason reason,
        Long winnerId,
        boolean blockTwoEnds,
        Map<String, Integer> ratingChange,
        int totalMoves,
        Instant startedAt,
        Instant endedAt) {

    static MatchSummary of(MatchRecord m, Map<Long, PlayerInfo> players) {
        Map<String, Integer> ratingChange = new LinkedHashMap<>();
        ratingChange.put("X", m.getRatingChangeX());
        ratingChange.put("O", m.getRatingChangeO());
        return new MatchSummary(m.getId(), m.getRoomId(), players.get(m.getPlayerXId()),
                players.get(m.getPlayerOId()), m.getResult(), m.getEndReason(), m.getWinnerId(), m.isBlockTwoEnds(),
                ratingChange, m.getTotalMoves(), m.getStartedAt(), m.getEndedAt());
    }
}
