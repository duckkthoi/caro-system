package com.caro.server.player;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.caro.server.game.Mark;
import com.caro.server.match.EndReason;
import com.caro.server.match.MatchRecord;
import com.caro.server.match.MatchResultType;
import com.caro.server.user.PlayerInfo;

/** One line of a player's match history, seen from that player. */
public record HistoryItem(
        UUID matchId,
        PlayerInfo opponent,
        Mark mark,
        Outcome outcome,
        EndReason reason,
        int ratingChange,
        int totalMoves,
        Instant startedAt,
        Instant endedAt) {

    static HistoryItem of(MatchRecord m, long userId, Map<Long, PlayerInfo> players) {
        boolean isX = m.getPlayerXId() == userId;
        long opponentId = isX ? m.getPlayerOId() : m.getPlayerXId();
        Outcome outcome;
        if (m.getResult() == MatchResultType.ABORTED) {
            outcome = Outcome.ABORTED;
        } else if (m.getResult() == MatchResultType.DRAW) {
            outcome = Outcome.DRAW;
        } else {
            outcome = m.getWinnerId() != null && m.getWinnerId() == userId ? Outcome.WIN : Outcome.LOSS;
        }
        return new HistoryItem(m.getId(), players.get(opponentId), isX ? Mark.X : Mark.O, outcome,
                m.getEndReason(), isX ? m.getRatingChangeX() : m.getRatingChangeO(), m.getTotalMoves(),
                m.getStartedAt(), m.getEndedAt());
    }
}
