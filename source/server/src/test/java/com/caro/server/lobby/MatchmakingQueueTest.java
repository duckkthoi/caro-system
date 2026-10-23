package com.caro.server.lobby;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.caro.server.config.CaroProperties;
import com.caro.server.user.PlayerInfo;

class MatchmakingQueueTest {

    // range: 100 at first, +50 every 5 s, anyone after 30 s
    private final MatchmakingQueue queue = new MatchmakingQueue(new CaroProperties.Matchmaking(
            100, 50, Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(1)));

    private static PlayerInfo player(long id, int rating) {
        return new PlayerInfo(id, "p" + id, "P" + id, rating);
    }

    private static long seconds(long s) {
        return s * 1000;
    }

    @Test
    void closeRatingsArePairedRightAway() {
        queue.add(player(1, 1000), 0);
        queue.add(player(2, 1080), 0);

        List<MatchmakingQueue.Pair> pairs = queue.takePairs(0);

        assertThat(pairs).hasSize(1);
        assertThat(queue.size()).isZero();
    }

    @Test
    void aLargeGapNeedsBothPlayersToWaitLongEnough() {
        queue.add(player(1, 1000), 0);
        queue.add(player(2, 1180), seconds(10)); // gap 180

        assertThat(queue.takePairs(seconds(14))).as("player 1 accepts 200, player 2 only 100").isEmpty();
        // player 2 has waited 10 s: 100 + 2 * 50 = 200 >= 180
        assertThat(queue.takePairs(seconds(20))).hasSize(1);
    }

    @Test
    void anyoneIsAcceptedAfterTheLimit() {
        queue.add(player(1, 1000), 0);
        queue.add(player(2, 1900), 0);

        assertThat(queue.takePairs(seconds(29))).isEmpty();
        assertThat(queue.takePairs(seconds(30))).hasSize(1);
    }

    @Test
    void theLongestWaitingPlayerGetsTheClosestOpponent() {
        queue.add(player(1, 1000), 0);
        queue.add(player(2, 1090), seconds(1));
        queue.add(player(3, 1010), seconds(2));

        List<MatchmakingQueue.Pair> pairs = queue.takePairs(seconds(2));

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).first().player().id()).isEqualTo(1);
        assertThat(pairs.get(0).second().player().id()).isEqualTo(3);
        assertThat(queue.contains(2)).isTrue();
    }

    @Test
    void widenedReportsEachRangeChangeOnce() {
        MatchmakingQueue.Entry entry = queue.add(player(1, 1000), 0);

        assertThat(queue.widened(seconds(4))).isEmpty();
        assertThat(queue.widened(seconds(5))).containsExactly(entry);
        assertThat(queue.widened(seconds(6))).isEmpty();
        assertThat(queue.rangeOf(entry, seconds(6))).isEqualTo(150);
        assertThat(queue.widened(seconds(30))).containsExactly(entry);
        assertThat(queue.rangeOf(entry, seconds(30))).as("null = anyone").isNull();
    }
}
