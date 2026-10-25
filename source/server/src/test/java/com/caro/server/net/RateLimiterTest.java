package com.caro.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong clock = new AtomicLong();

    @Test
    void allowsABurstThenRefillsAtTheConfiguredRate() {
        RateLimiter limiter = new RateLimiter(3, 2.0, clock::get);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        clock.addAndGet(SECOND / 2); // 2 per second: half a second gives one token
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void neverStoresMoreThanTheBurst() {
        RateLimiter limiter = new RateLimiter(2, 1.0, clock::get);
        clock.addAndGet(60 * SECOND);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void rejectedAttemptsDoNotCost() {
        RateLimiter limiter = new RateLimiter(1, 1.0, clock::get);
        assertThat(limiter.tryAcquire()).isTrue();
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire()).isFalse();
        }
        clock.addAndGet(SECOND);
        assertThat(limiter.tryAcquire()).isTrue();
    }
}
