package com.caro.server.auth;

import com.caro.server.user.Role;
import com.caro.server.user.User;

/** Identity attached to a socket session or a REST request after authentication. */
public record AuthenticatedUser(long id, String username, Role role) {

    public static AuthenticatedUser from(User u) {
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }
}
