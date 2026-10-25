package com.caro.server.net;

import java.util.function.LongSupplier;

/**
 * Token bucket (docs/rules.md, section 13): allows bursts of up to {@code burst} actions, then {@code perSecond}
 * actions per second on average. Each action takes one token; tokens refill continuously up to {@code burst}.
 * Thread-safe.
 */
public final class RateLimiter {

    private final double burst;
    private final double tokensPerNano;
    private final LongSupplier nanoTime;
    // ---- guarded by "this" ----
    private double tokens;
    private long lastRefill;

    public RateLimiter(int burst, double perSecond) {
        this(burst, perSecond, System::nanoTime);
    }

    /** @param nanoTime a clock in nanoseconds, replaceable in tests */
    RateLimiter(int burst, double perSecond, LongSupplier nanoTime) {
        this.burst = burst;
        this.tokensPerNano = perSecond / 1_000_000_000.0;
        this.nanoTime = nanoTime;
        this.tokens = burst;
        this.lastRefill = nanoTime.getAsLong();
    }

    /** @return true if the action may go ahead (a token was taken), false if it must be rejected */
    public synchronized boolean tryAcquire() {
        long now = nanoTime.getAsLong();
        tokens = Math.min(burst, tokens + (now - lastRefill) * tokensPerNano);
        lastRefill = now;
        if (tokens < 1) {
            return false;
        }
        tokens -= 1;
        return true;
    }
}
