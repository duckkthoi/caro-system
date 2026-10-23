package com.caro.server.net;

/** Message types a client may send (docs/protocol.md, section 3). */
public enum ClientMessageType {
    PING(false),
    REGISTER(false),
    LOGIN(false),
    AUTH(false),
    LOGOUT(true),
    GET_STATE(true),
    LIST_ROOMS(true),
    CREATE_ROOM(true),
    JOIN_ROOM(true),
    LEAVE_ROOM(true),
    MOVE(true),
    RESIGN(true),
    GET_PROFILE(true),
    GET_HISTORY(true),
    GET_MATCH(true),
    GET_LEADERBOARD(true);

    private final boolean requiresAuth;

    ClientMessageType(boolean requiresAuth) {
        this.requiresAuth = requiresAuth;
    }

    public boolean requiresAuth() {
        return requiresAuth;
    }

    /** @return the type, or null if the name is unknown */
    public static ClientMessageType parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
