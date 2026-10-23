package com.caro.client.model;

/** INVITED event. */
public record Invitation(String inviteId, PlayerInfo from, long expiresAt) {
}
