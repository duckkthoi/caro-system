package com.caro.server.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code caro.*} section in application.yml. */
@ConfigurationProperties(prefix = "caro")
public record CaroProperties(Tcp tcp, Game game, Heartbeat heartbeat, Jwt jwt, Admin admin, Cors cors) {

    public record Tcp(int port, int maxLineLength, int maxOutboundQueue) {
    }

    public record Game(int boardSize, int winLength, Duration turnTime, Duration reconnectGrace) {
    }

    public record Heartbeat(Duration idleTimeout, Duration checkInterval) {
    }

    public record Jwt(String secret, Duration ttl) {
    }

    public record Admin(String username, String password) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
