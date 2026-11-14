package com.caro.server.match;

public enum EndReason {
    FIVE_IN_ROW,
    BOARD_FULL,
    RESIGN,
    TIMEOUT,
    DISCONNECT,
    BOTH_DISCONNECTED,
    /** Both players agreed to a draw (docs/rules.md, section 14). */
    AGREED
}
