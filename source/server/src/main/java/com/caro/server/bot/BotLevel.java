package com.caro.server.bot;

/** Strength of the computer opponent (docs/rules.md, section 12). */
public enum BotLevel {
    /** Defends half-heartedly and picks among its few best moves at random. */
    EASY,
    /** Always plays the best move it finds. */
    HARD;

    public String displayName() {
        return this == EASY ? "Máy (dễ)" : "Máy (khó)";
    }
}
