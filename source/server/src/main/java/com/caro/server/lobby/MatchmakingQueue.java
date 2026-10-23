package com.caro.server.lobby;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.caro.server.config.CaroProperties;
import com.caro.server.user.PlayerInfo;

/**
 * The quick-match queue and its pairing rule (docs/rules.md, section 8). Pure logic: no locking, no I/O.
 * Not thread-safe: {@link LobbyService} only uses it while holding the lobby lock.
 *
 * <p>Each waiting player accepts opponents within a rating range that widens the longer they wait.
 * Two players are paired when their rating gap fits <b>both</b> ranges. The player who has waited the longest
 * is served first and gets the closest compatible opponent.
 */
final class MatchmakingQueue {

    /** A waiting player. {@code range == null} means any opponent is accepted. */
    static final class Entry {
        private final PlayerInfo player;
        private final long joinedAt;
        private Integer announcedRange;

        private Entry(PlayerInfo player, long joinedAt, Integer range) {
            this.player = player;
            this.joinedAt = joinedAt;
            this.announcedRange = range;
        }

        PlayerInfo player() {
            return player;
        }

        long joinedAt() {
            return joinedAt;
        }
    }

    record Pair(Entry first, Entry second) {
    }

    private final CaroProperties.Matchmaking settings;
    private final Map<Long, Entry> entries = new LinkedHashMap<>(); // iteration order = join order

    MatchmakingQueue(CaroProperties.Matchmaking settings) {
        this.settings = settings;
    }

    Entry add(PlayerInfo player, long now) {
        Entry entry = new Entry(player, now, rangeAfter(0));
        entries.put(player.id(), entry);
        return entry;
    }

    Entry remove(long userId) {
        return entries.remove(userId);
    }

    Entry get(long userId) {
        return entries.get(userId);
    }

    boolean contains(long userId) {
        return entries.containsKey(userId);
    }

    int size() {
        return entries.size();
    }

    /** @return the rating gap this entry accepts right now, or null for "anyone" */
    Integer rangeOf(Entry entry, long now) {
        return rangeAfter(now - entry.joinedAt);
    }

    /** Finds every pair that can play now and removes them from the queue. */
    List<Pair> takePairs(long now) {
        List<Pair> pairs = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        List<Entry> waiting = List.copyOf(entries.values());
        for (Entry a : waiting) {
            if (taken.contains(a.player.id())) {
                continue;
            }
            Entry best = null;
            int bestGap = Integer.MAX_VALUE;
            for (Entry b : waiting) {
                if (b == a || taken.contains(b.player.id())) {
                    continue;
                }
                int gap = Math.abs(a.player.rating() - b.player.rating());
                if (gap < bestGap && fits(gap, rangeOf(a, now)) && fits(gap, rangeOf(b, now))) {
                    best = b;
                    bestGap = gap;
                }
            }
            if (best != null) {
                taken.add(a.player.id());
                taken.add(best.player.id());
                pairs.add(new Pair(a, best));
            }
        }
        taken.forEach(entries::remove);
        return pairs;
    }

    /** @return the entries whose range changed since the last call, so the players can be told */
    List<Entry> widened(long now) {
        List<Entry> changed = new ArrayList<>();
        for (Entry e : entries.values()) {
            Integer range = rangeOf(e, now);
            if (!Objects.equals(range, e.announcedRange)) {
                e.announcedRange = range;
                changed.add(e);
            }
        }
        return changed;
    }

    private Integer rangeAfter(long waitedMs) {
        if (waitedMs >= settings.matchAnyoneAfter().toMillis()) {
            return null;
        }
        long steps = waitedMs / settings.stepInterval().toMillis();
        return (int) (settings.initialRange() + steps * settings.rangeStep());
    }

    private static boolean fits(int gap, Integer range) {
        return range == null || gap <= range;
    }
}
