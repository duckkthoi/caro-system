package com.caro.server.lobby;

import com.caro.server.user.PlayerInfo;
import com.caro.server.user.Role;

/** One line of the online list (LIST_ONLINE, ONLINE_USERS). Clients hide the invite button for admins. */
public record OnlineUser(long id, String username, String displayName, int rating, Role role,
                         PresenceStatus status) {

    static OnlineUser of(PlayerInfo p, Role role, PresenceStatus status) {
        return new OnlineUser(p.id(), p.username(), p.displayName(), p.rating(), role, status);
    }
}
