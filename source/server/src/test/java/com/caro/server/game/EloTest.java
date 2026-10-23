package com.caro.server.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EloTest {

    @Test
    void equalRatingsWinGivesHalfOfK() {
        assertThat(Elo.deltaForX(1000, 1000, 1.0)).isEqualTo(16);
        assertThat(Elo.deltaForX(1000, 1000, 0.0)).isEqualTo(-16);
        assertThat(Elo.deltaForX(1000, 1000, 0.5)).isZero();
    }

    @Test
    void beatingAStrongerPlayerGivesMore() {
        assertThat(Elo.deltaForX(1000, 1200, 1.0)).isEqualTo(24);
        assertThat(Elo.deltaForX(1200, 1000, 1.0)).isEqualTo(8);
    }

    @Test
    void drawAgainstStrongerPlayerGainsPoints() {
        assertThat(Elo.deltaForX(1000, 1200, 0.5)).isPositive();
    }
}
