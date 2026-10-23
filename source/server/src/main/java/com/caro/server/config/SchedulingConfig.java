package com.caro.server.config;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulingConfig {

    /** Runs turn timers, reconnect-grace timers and the heartbeat sweep. */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService gameScheduler() {
        return Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("game-timer-", 0).daemon().factory());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
