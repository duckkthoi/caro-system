package com.caro.server.bot;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.caro.server.common.AppException;
import com.caro.server.match.GameMatch;
import com.caro.server.match.MatchState;
import com.caro.server.match.MatchStatus;
import com.caro.server.net.Notifier;
import com.caro.server.net.ServerMessageType;
import com.caro.server.user.PlayerInfo;

/**
 * The computer opponent of one match (docs/rules.md, section 12).
 *
 * <p>It behaves like a client living inside the server: the match's {@link Notifier} hands it the events meant for
 * the bot's user id, it looks at the same snapshot a client gets, and it plays through {@link GameMatch#move}, so
 * its moves are checked by exactly the same rules as a human's.
 *
 * <p>Events arrive while the match lock is held, so the bot never plays from inside the event: it only schedules
 * a "think" on the game scheduler. When that runs, it re-reads the snapshot and plays only if it is still its turn.
 */
public final class BotPlayer {

    /** User id of the bot in matches and rooms. Real users have positive ids. */
    public static final long ID = -1;

    private static final Logger log = LoggerFactory.getLogger(BotPlayer.class);

    private final BotLevel level;
    private final ScheduledExecutorService scheduler;
    private final long moveDelayMs;
    private volatile GameMatch match;

    public BotPlayer(BotLevel level, ScheduledExecutorService scheduler, long moveDelayMs) {
        this.level = level;
        this.scheduler = scheduler;
        this.moveDelayMs = moveDelayMs;
    }

    public static PlayerInfo info(BotLevel level) {
        return new PlayerInfo(ID, "bot", level.displayName(), 1000);
    }

    public static boolean isBot(long userId) {
        return userId == ID;
    }

    /** A notifier for the match: events for the bot go to the bot, everything else to {@code humans}. */
    public Notifier wrap(Notifier humans) {
        return (userId, type, data) -> {
            if (isBot(userId)) {
                onEvent(type);
            } else {
                humans.sendToUser(userId, type, data);
            }
        };
    }

    /** Must be called before the match starts. */
    public void attach(GameMatch match) {
        this.match = match;
    }

    private void onEvent(ServerMessageType type) {
        if (type == ServerMessageType.MATCH_START || type == ServerMessageType.MOVE_APPLIED
                || type == ServerMessageType.OPPONENT_RECONNECTED) {
            scheduler.schedule(this::think, moveDelayMs, TimeUnit.MILLISECONDS);
        }
    }

    /** Package-private for tests. */
    void think() {
        GameMatch m = match;
        try {
            MatchState s = m.snapshotFor(ID);
            if (s.status() != MatchStatus.IN_PROGRESS || s.turn() != s.yourMark()) {
                return;
            }
            int[] move = BotBrain.chooseMove(s.board(), s.yourMark().symbol(), s.blockTwoEnds(), level,
                    ThreadLocalRandom.current());
            m.move(ID, move[0], move[1]);
        } catch (AppException e) {
            // the player resigned, left or the match paused between the snapshot and the move: nothing to do
            log.debug("Bot move in match {} skipped: {}", m.id(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Bot failed in match {}", m.id(), e);
        }
    }
}
