package com.caro.client.model;

/** status: IDLE | IN_QUEUE | IN_ROOM | PLAYING | SPECTATING. */
public record OnlineUser(long id, String username, String displayName, int rating, String status) {

    /** Only these players can be invited (docs/rules.md, sections 8.3 and 10). */
    public boolean invitable() {
        return "IDLE".equals(status) || "IN_QUEUE".equals(status) || "SPECTATING".equals(status);
    }
}
