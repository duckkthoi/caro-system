package com.caro.server.player;

/** Aborted matches are not counted. winRate is a percentage with one decimal, e.g. 66.7. */
public record PlayerStats(long played, long wins, long losses, long draws, double winRate) {

    public static PlayerStats of(long played, long wins, long draws) {
        double winRate = played == 0 ? 0 : Math.round(wins * 1000.0 / played) / 10.0;
        return new PlayerStats(played, wins, played - wins - draws, draws, winRate);
    }
}
