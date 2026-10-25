package com.caro.client.model;

/** CHAT_MESSAGE event. sentAt is epoch ms on the server clock. */
public record ChatMessage(String roomId, PlayerInfo from, boolean spectator, String text, long sentAt) {
}
