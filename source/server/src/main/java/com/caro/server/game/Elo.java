package com.caro.server.game;

/** Elo rating change (docs/rules.md, section 6). */
public final class Elo {

    public static final int K = 32;

    private Elo() {
    }

    /**
     * @param scoreX 1 if X won, 0.5 for a draw, 0 if X lost
     * @return the rating change for X; O always gets the negated value
     */
    public static int deltaForX(int ratingX, int ratingO, double scoreX) {
        double expectedX = 1.0 / (1.0 + Math.pow(10, (ratingO - ratingX) / 400.0));
        return (int) Math.round(K * (scoreX - expectedX));
    }
}
