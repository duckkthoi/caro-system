package com.caro.server.admin;

import java.time.Instant;

import com.caro.server.user.Role;
import com.caro.server.user.User;
import com.caro.server.user.UserStatus;

/** What the admin screen shows about an account. */
public record AdminUserView(
        long id,
        String username,
        String displayName,
        Role role,
        UserStatus status,
        int rating,
        Instant createdAt,
        boolean online) {

    static AdminUserView of(User u, boolean online) {
        return new AdminUserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getStatus(),
                u.getRating(), u.getCreatedAt(), online);
    }
}
