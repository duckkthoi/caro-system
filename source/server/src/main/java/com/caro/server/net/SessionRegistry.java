package com.caro.server.net;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.caro.server.auth.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Knows every open session and which session belongs to which user.
 * Enforces "one active session per account" (docs/rules.md, section 5).
 */
@Component
public class SessionRegistry implements Notifier {

    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, ClientSession> byUser = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public SessionRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    void add(ClientSession session) {
        sessions.put(session.id(), session);
    }

    /**
     * Marks the session as logged in as {@code user}.
     *
     * @return the user's previous session (which the caller must kick), or null
     */
    ClientSession bindUser(ClientSession session, AuthenticatedUser user) {
        session.setUser(user);
        return byUser.put(user.id(), session);
    }

    /**
     * Forgets a closed session.
     *
     * @return true if it was the user's active session. False for anonymous sessions and for sessions that were
     *         already replaced by a newer login, in which case the user did not really go offline.
     */
    boolean remove(ClientSession session) {
        sessions.remove(session.id());
        AuthenticatedUser user = session.user();
        return user != null && byUser.remove(user.id(), session);
    }

    /**
     * Sends KICKED to the user's active session, then closes it. The disconnect is then handled like any other
     * (a running match is paused and waits for the reconnect grace period).
     *
     * @return false if the user was not online
     */
    public boolean kick(long userId, String reason) {
        ClientSession session = byUser.get(userId);
        if (session == null) {
            return false;
        }
        kick(session, reason);
        return true;
    }

    void kick(ClientSession session, String reason) {
        send(session, ServerMessageType.KICKED, null, Map.of("reason", reason));
        session.closeAfterFlush();
    }

    public boolean isOnline(long userId) {
        return byUser.containsKey(userId);
    }

    public int onlineCount() {
        return byUser.size();
    }

    public Collection<ClientSession> all() {
        return List.copyOf(sessions.values());
    }

    public void send(ClientSession session, ServerMessageType type, String reqId, Object data) {
        session.send(toJson(new OutboundMessage(type, reqId, data)));
    }

    @Override
    public void sendToUser(long userId, ServerMessageType type, Object data) {
        ClientSession session = byUser.get(userId);
        if (session != null) {
            send(session, type, null, data);
        }
    }

    /** Sends the same event to every logged-in user. The JSON is built only once. */
    public void broadcast(ServerMessageType type, Object data) {
        String json = toJson(new OutboundMessage(type, null, data));
        for (ClientSession session : byUser.values()) {
            session.send(json);
        }
    }

    private String toJson(OutboundMessage message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + message.type(), e);
        }
    }
}
