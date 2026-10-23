package com.caro.server.user;

/** What a logged-in user sees about themself. */
public record UserInfo(long id, String username, String displayName, Role role, int rating) {

    public static UserInfo from(User u) {
        return new UserInfo(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getRating());
    }
}
