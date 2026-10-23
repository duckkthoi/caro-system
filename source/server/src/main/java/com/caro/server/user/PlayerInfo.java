package com.caro.server.user;

/** Public information about a player, shown to other players. */
public record PlayerInfo(long id, String username, String displayName, int rating) {

    public static PlayerInfo from(User u) {
        return new PlayerInfo(u.getId(), u.getUsername(), u.getDisplayName(), u.getRating());
    }
}
