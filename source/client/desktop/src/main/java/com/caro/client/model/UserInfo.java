package com.caro.client.model;

/** The logged-in user. */
public record UserInfo(long id, String username, String displayName, String role, int rating) {

    public UserInfo withRating(int newRating) {
        return new UserInfo(id, username, displayName, role, newRating);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
