package com.caro.server.match;

/**
 * Called exactly once per match, after it finished. Always invoked <b>without</b> holding the match lock,
 * so the listener may take other locks (e.g. the lobby lock) or write to the database.
 */
public interface MatchEndListener {

    void onMatchEnded(GameMatch match);
}
