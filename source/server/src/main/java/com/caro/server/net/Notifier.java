package com.caro.server.net;

/**
 * Pushes an event to a user, whichever transport (TCP or WebSocket) they are connected with.
 * Never blocks: the message is queued on the user's session. Offline users are skipped.
 */
public interface Notifier {

    void sendToUser(long userId, ServerMessageType type, Object data);
}
