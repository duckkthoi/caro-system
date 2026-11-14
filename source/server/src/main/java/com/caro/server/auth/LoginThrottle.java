package com.caro.server.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.caro.server.common.AppException;
import com.caro.server.common.ErrorCode;
import com.caro.server.config.CaroProperties;
import com.caro.server.net.RateLimiter;

import jakarta.annotation.PostConstruct;

/**
 * Limits login and registration attempts per IP address (docs/rules.md, section 13), for REST and socket alike,
 * so that guessing passwords is slow even when the attacker opens a new connection for every try.
 * Buckets of addresses that have been quiet for a while are dropped, so the map does not grow forever.
 */
@Component
public class LoginThrottle {

    private static final long IDLE_NANOS = TimeUnit.MINUTES.toNanos(10);

    private final Map<String, RateLimiter> buckets = new ConcurrentHashMap<>();
    private final CaroProperties.RateLimit limits;
    private final ScheduledExecutorService scheduler;

    public LoginThrottle(CaroProperties props, ScheduledExecutorService gameScheduler) {
        this.limits = props.rateLimit();
        this.scheduler = gameScheduler;
    }

    @PostConstruct
    void startCleanup() {
        scheduler.scheduleWithFixedDelay(() -> buckets.values().removeIf(b -> b.isIdleFor(IDLE_NANOS)),
                1, 1, TimeUnit.MINUTES);
    }

    /** Counts one attempt from this address. @throws AppException RATE_LIMITED when there were too many */
    public void check(String ip) {
        RateLimiter bucket = buckets.computeIfAbsent(ip,
                k -> new RateLimiter(limits.authBurst(), limits.authPerSecond()));
        if (!bucket.tryAcquire()) {
            throw new AppException(ErrorCode.RATE_LIMITED,
                    "Bạn đăng nhập hoặc đăng ký quá nhiều lần, hãy thử lại sau ít phút");
        }
    }
}
