package com.caro.server.net;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.caro.server.config.CaroProperties;

import jakarta.annotation.PostConstruct;

/**
 * Detects dead connections. TCP alone may not notice a peer that vanished (cable pulled, Wi-Fi lost) for a
 * very long time, so clients must send PING every 10 s and any session silent for longer than
 * caro.heartbeat.idle-timeout is closed (docs/rules.md, section 4).
 */
@Component
public class HeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final SessionRegistry registry;
    private final MessageDispatcher dispatcher;
    private final ScheduledExecutorService scheduler;
    private final long idleTimeoutMs;
    private final long checkIntervalMs;

    public HeartbeatMonitor(SessionRegistry registry, MessageDispatcher dispatcher,
                            ScheduledExecutorService gameScheduler, CaroProperties props) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.scheduler = gameScheduler;
        this.idleTimeoutMs = props.heartbeat().idleTimeout().toMillis();
        this.checkIntervalMs = props.heartbeat().checkInterval().toMillis();
    }

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(this::sweep, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
    }

    void sweep() {
        try {
            long now = System.currentTimeMillis();
            for (ClientSession session : registry.all()) {
                if (now - session.lastSeenAt() > idleTimeoutMs) {
                    log.info("{}: no message for {} ms, closing", session.id(), now - session.lastSeenAt());
                    session.close();
                    dispatcher.onClose(session);
                }
            }
        } catch (RuntimeException e) {
            log.error("Heartbeat sweep failed", e);
        }
    }
}
