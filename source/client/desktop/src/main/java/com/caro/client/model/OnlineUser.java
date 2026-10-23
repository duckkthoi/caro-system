package com.caro.client.model;

/** status: IDLE | IN_QUEUE | IN_ROOM | PLAYING. */
public record OnlineUser(long id, String username, String displayName, int rating, String status) {

    /** Only these players can be invited (docs/rules.md, section 8.3). */
    public boolean invitable() {
        return "IDLE".equals(status) || "IN_QUEUE".equals(status);
    }
}
