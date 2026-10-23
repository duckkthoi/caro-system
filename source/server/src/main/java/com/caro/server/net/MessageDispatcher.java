package com.caro.server.net;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.caro.server.auth.AuthService;
import com.caro.server.auth.AuthenticatedUser;
import com.caro.server.common.AppException;
import com.caro.server.common.ErrorBody;
import com.caro.server.common.ErrorCode;
import com.caro.server.lobby.LobbyService;
import com.caro.server.player.PlayerQueryService;
import com.caro.server.user.User;
import com.caro.server.user.UserInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Transport-independent entry point of the game protocol. Both TcpServer and GameWebSocketHandler only
 * turn bytes into strings and call {@link #onOpen}, {@link #onMessage} and {@link #onClose}.
 * Messages of one session are processed one after another (by that session's reader thread).
 */
@Component
public class MessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    record RegisterRequest(String username, String password, String displayName) {
    }

    record LoginRequest(String username, String password) {
    }

    record AuthRequest(String token) {
    }

    record CreateRoomRequest(String name) {
    }

    record JoinRoomRequest(String roomId) {
    }

    record MoveRequest(String matchId, Integer x, Integer y) {
    }

    record ResignRequest(String matchId) {
    }

    record ProfileRequest(Long userId) {
    }

    record HistoryRequest(Long userId, Integer page, Integer size) {
    }

    record MatchRequest(String matchId) {
    }

    record LeaderboardRequest(Integer page, Integer size) {
    }

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ObjectMapper mapper;
    private final SessionRegistry registry;
    private final AuthService auth;
    private final LobbyService lobby;
    private final PlayerQueryService queries;

    public MessageDispatcher(ObjectMapper mapper, SessionRegistry registry, AuthService auth, LobbyService lobby,
                             PlayerQueryService queries) {
        this.mapper = mapper;
        this.registry = registry;
        this.auth = auth;
        this.lobby = lobby;
        this.queries = queries;
    }

    public void onOpen(ClientSession session) {
        registry.add(session);
        log.info("{} connected from {}", session.id(), session.remoteAddress());
    }

    public void onMessage(ClientSession session, String raw) {
        session.touch();
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (JsonProcessingException e) {
            root = null;
        }
        if (root == null || !root.isObject()) {
            sendError(session, null, ErrorCode.BAD_REQUEST, "Thông điệp phải là một object JSON");
            return;
        }

        String reqId = root.hasNonNull("reqId") ? root.get("reqId").asText() : null;
        String typeName = root.hasNonNull("type") ? root.get("type").asText() : null;
        ClientMessageType type = ClientMessageType.parse(typeName);
        try {
            if (type == null) {
                throw new AppException(ErrorCode.UNKNOWN_TYPE, "type không được hỗ trợ: " + typeName);
            }
            if (type.requiresAuth() && session.user() == null) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Cần đăng nhập trước");
            }
            handle(session, type, reqId, root.path("data"));
        } catch (AppException e) {
            sendError(session, reqId, e.code(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("{}: failed to handle {}", session.id(), typeName, e);
            sendError(session, reqId, ErrorCode.INTERNAL_ERROR, "Lỗi hệ thống");
        }
    }

    /**
     * Called once per session when its connection is gone (closed by either side, error or heartbeat timeout).
     * The transport closes the connection itself; this only updates the game state.
     */
    public void onClose(ClientSession session) {
        if (!session.markReleased()) {
            return;
        }
        boolean wasActive = registry.remove(session);
        log.info("{} disconnected", session.id());
        if (wasActive) {
            lobby.onUserDisconnected(session.user().id());
        }
    }

    private void handle(ClientSession session, ClientMessageType type, String reqId, JsonNode data) {
        switch (type) {
            case PING -> registry.send(session, ServerMessageType.PONG, reqId,
                    Map.of("serverTime", System.currentTimeMillis()));
            case REGISTER -> {
                RegisterRequest req = read(data, RegisterRequest.class);
                User user = auth.register(req.username(), req.password(), req.displayName());
                ok(session, reqId, Map.of("user", UserInfo.from(user)));
            }
            case LOGIN -> {
                requireAnonymous(session);
                LoginRequest req = read(data, LoginRequest.class);
                User user = auth.login(req.username(), req.password());
                completeLogin(session, reqId, user, auth.issueToken(user));
            }
            case AUTH -> {
                requireAnonymous(session);
                AuthRequest req = read(data, AuthRequest.class);
                completeLogin(session, reqId, auth.authenticate(req.token()), null);
            }
            case LOGOUT -> {
                lobby.leaveRoomIfPresent(userId(session));
                ok(session, reqId, Map.of());
                session.closeAfterFlush();
            }
            case GET_STATE -> ok(session, reqId, lobby.getState(userId(session)));
            case LIST_ROOMS -> ok(session, reqId, Map.of("rooms", lobby.listRooms()));
            case CREATE_ROOM -> {
                CreateRoomRequest req = read(data, CreateRoomRequest.class);
                ok(session, reqId, Map.of("room", lobby.createRoom(userId(session), req.name())));
            }
            case JOIN_ROOM -> {
                JoinRoomRequest req = read(data, JoinRoomRequest.class);
                requireField(req.roomId(), "roomId");
                ok(session, reqId, Map.of("room", lobby.joinRoom(userId(session), req.roomId())));
            }
            case LEAVE_ROOM -> {
                lobby.leaveRoom(userId(session));
                ok(session, reqId, Map.of());
            }
            case MOVE -> {
                MoveRequest req = read(data, MoveRequest.class);
                requireField(req.matchId(), "matchId");
                requireField(req.x(), "x");
                requireField(req.y(), "y");
                int moveNo = lobby.move(userId(session), req.matchId(), req.x(), req.y());
                ok(session, reqId, Map.of("moveNo", moveNo));
            }
            case RESIGN -> {
                ResignRequest req = read(data, ResignRequest.class);
                requireField(req.matchId(), "matchId");
                lobby.resign(userId(session), req.matchId());
                ok(session, reqId, Map.of());
            }
            // read-only queries: same JSON as the matching REST endpoints
            case GET_PROFILE -> {
                ProfileRequest req = read(data, ProfileRequest.class);
                ok(session, reqId, queries.profile(req.userId() != null ? req.userId() : userId(session)));
            }
            case GET_HISTORY -> {
                HistoryRequest req = read(data, HistoryRequest.class);
                long target = req.userId() != null ? req.userId() : userId(session);
                ok(session, reqId, queries.history(target, orDefault(req.page(), 0),
                        orDefault(req.size(), DEFAULT_PAGE_SIZE)));
            }
            case GET_MATCH -> {
                MatchRequest req = read(data, MatchRequest.class);
                requireField(req.matchId(), "matchId");
                ok(session, reqId, queries.match(parseUuid(req.matchId(), "matchId")));
            }
            case GET_LEADERBOARD -> {
                LeaderboardRequest req = read(data, LeaderboardRequest.class);
                ok(session, reqId, queries.leaderboard(orDefault(req.page(), 0),
                        orDefault(req.size(), DEFAULT_PAGE_SIZE)));
            }
        }
    }

    /**
     * Binds the session to the user, kicks the user's older session if any, then lets the lobby
     * resend room / match state (this is how reconnecting works).
     */
    private void completeLogin(ClientSession session, String reqId, User user, String token) {
        ClientSession previous = registry.bindUser(session, AuthenticatedUser.from(user));
        if (previous != null && previous != session) {
            log.info("{}: user {} logged in elsewhere ({}), kicking", previous.id(), user.getUsername(), session.id());
            registry.kick(previous, "LOGGED_IN_ELSEWHERE");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (token != null) {
            body.put("token", token);
        }
        body.put("user", UserInfo.from(user));
        ok(session, reqId, body);
        lobby.onUserConnected(user.getId());
    }

    private void ok(ClientSession session, String reqId, Object data) {
        registry.send(session, ServerMessageType.OK, reqId, data);
    }

    private void sendError(ClientSession session, String reqId, ErrorCode code, String message) {
        registry.send(session, ServerMessageType.ERROR, reqId, new ErrorBody(code, message));
    }

    private <T> T read(JsonNode data, Class<T> type) {
        try {
            JsonNode node = data == null || data.isMissingNode() || data.isNull() ? mapper.createObjectNode() : data;
            return mapper.treeToValue(node, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "data không đúng định dạng");
        }
    }

    private static void requireAnonymous(ClientSession session) {
        if (session.user() != null) {
            throw new AppException(ErrorCode.ALREADY_AUTHENTICATED, "Phiên này đã đăng nhập");
        }
    }

    private static void requireField(Object value, String name) {
        if (value == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thiếu trường " + name);
        }
    }

    private static UUID parseUuid(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, name + " không hợp lệ");
        }
    }

    private static int orDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static long userId(ClientSession session) {
        return session.user().id();
    }
}
