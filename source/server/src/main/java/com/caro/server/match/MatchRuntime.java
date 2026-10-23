package com.caro.server.match;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;

import com.caro.server.config.CaroProperties;
import com.caro.server.net.Notifier;

/** Everything a {@link GameMatch} needs from the outside world. Lets tests plug in fakes. */
public record MatchRuntime(
        CaroProperties.Game settings,
        Notifier notifier,
        ScheduledExecutorService scheduler,
        MatchEndListener listener,
        Clock clock) {
}
