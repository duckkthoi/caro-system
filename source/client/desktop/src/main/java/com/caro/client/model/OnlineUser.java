package com.caro.client.model;

/** role: PLAYER | ADMIN. status: IDLE | IN_QUEUE | IN_ROOM | PLAYING | SPECTATING. */
public record OnlineUser(long id, String username, String displayName, int rating, String role, String status) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /** Only these players can be invited (docs/rules.md, sections 8.3, 10 and 15). */
    public boolean invitable() {
        return !isAdmin()
                && ("IDLE".equals(status) || "IN_QUEUE".equals(status) || "SPECTATING".equals(status));
    }
}
