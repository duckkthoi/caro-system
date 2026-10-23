package com.caro.server.lobby;

import com.caro.server.user.PlayerInfo;

/** One line of the online list (LIST_ONLINE, ONLINE_USERS). */
public record OnlineUser(long id, String username, String displayName, int rating, PresenceStatus status) {

    static OnlineUser of(PlayerInfo p, PresenceStatus status) {
        return new OnlineUser(p.id(), p.username(), p.displayName(), p.rating(), status);
    }
}
