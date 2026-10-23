package com.caro.server.lobby;

/** Why an invitation is no longer pending (INVITE_CLOSED event). */
public enum InviteCloseReason {
    ACCEPTED,
    DECLINED,
    /** The inviter withdrew it. */
    CANCELLED,
    EXPIRED,
    /** One side went offline or started another match. */
    UNAVAILABLE
}
