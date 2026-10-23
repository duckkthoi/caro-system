package com.caro.server.match;

import com.caro.server.game.Mark;

public enum MatchResultType {
    X_WIN,
    O_WIN,
    DRAW,
    /** Cancelled without a winner; ratings are not changed. */
    ABORTED;

    public static MatchResultType winOf(Mark winner) {
        return winner == Mark.X ? X_WIN : O_WIN;
    }
}
