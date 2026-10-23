package com.caro.server.match;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, UUID> {

    /** Win/draw counts of one player, aborted matches excluded. Losses = played - wins - draws. */
    interface StatsRow {
        long getPlayed();

        long getWins();

        long getDraws();
    }

    @Query("select m from MatchRecord m where m.playerXId = :userId or m.playerOId = :userId")
    Page<MatchRecord> findByPlayer(@Param("userId") long userId, Pageable pageable);

    @Query("""
            select count(m) as played,
                   coalesce(sum(case when m.winnerId = :userId then 1 else 0 end), 0) as wins,
                   coalesce(sum(case when m.result = com.caro.server.match.MatchResultType.DRAW then 1 else 0 end), 0)
                       as draws
            from MatchRecord m
            where (m.playerXId = :userId or m.playerOId = :userId)
              and m.result <> com.caro.server.match.MatchResultType.ABORTED
            """)
    StatsRow statsOf(@Param("userId") long userId);

    long countByEndedAtAfter(Instant since);
}
