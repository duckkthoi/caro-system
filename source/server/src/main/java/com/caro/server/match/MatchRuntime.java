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

    /** Same runtime with another notifier (a match against the bot routes the bot's events to it). */
    public MatchRuntime withNotifier(Notifier other) {
        return new MatchRuntime(settings, other, scheduler, listener, clock);
    }
}
