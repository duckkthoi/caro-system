package com.caro.server.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code caro.*} section in application.yml. */
@ConfigurationProperties(prefix = "caro")
public record CaroProperties(Tcp tcp, Game game, Matchmaking matchmaking, Invite invite, Heartbeat heartbeat, Jwt jwt,
                             Admin admin, Cors cors, RateLimit rateLimit, Bot bot) {

    public record Tcp(int port, int maxLineLength, int maxOutboundQueue) {
    }

    public record Game(int boardSize, int winLength, Duration turnTime, Duration reconnectGrace) {
    }

    /**
     * Quick match: two players are paired when their rating gap fits both of their ranges. A range starts at
     * {@code initialRange}, grows by {@code rangeStep} every {@code stepInterval} of waiting, and becomes
     * unlimited after {@code matchAnyoneAfter}.
     */
    public record Matchmaking(int initialRange, int rangeStep, Duration stepInterval, Duration matchAnyoneAfter,
                              Duration checkInterval) {
    }

    public record Invite(Duration timeout) {
    }

    public record Heartbeat(Duration idleTimeout, Duration checkInterval) {
    }

    public record Jwt(String secret, Duration ttl) {
    }

    public record Admin(String username, String password) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * Per connection token buckets (docs/rules.md, section 13): every message but PING takes a token from the
     * general bucket, CHAT also from the chat bucket.
     */
    public record RateLimit(int burst, double perSecond, int chatBurst, double chatPerSecond) {
    }

    /** The computer opponent waits {@code moveDelay} before each move, so it feels like a player. */
    public record Bot(Duration moveDelay) {
    }
}
