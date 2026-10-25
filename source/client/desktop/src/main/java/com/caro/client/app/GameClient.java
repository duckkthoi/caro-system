package com.caro.client.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.caro.client.app.ClientListener.ConnectionStatus;
import com.caro.client.model.ChatMessage;
import com.caro.client.model.HistoryItem;
import com.caro.client.model.Invitation;
import com.caro.client.model.LeaderboardEntry;
import com.caro.client.model.MatchDetail;
import com.caro.client.model.MatchResult;
import com.caro.client.model.MatchState;
import com.caro.client.model.MoveApplied;
import com.caro.client.model.OnlineUser;
import com.caro.client.model.Page;
import com.caro.client.model.Profile;
import com.caro.client.model.QueueStatus;
import com.caro.client.model.RoomInfo;
import com.caro.client.model.UserInfo;
import com.caro.client.net.Connection;
import com.caro.client.net.Json;
import com.caro.client.net.ServerException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Everything the screens need: actions (login, move, invite...) and the current state (me, rooms, my room,
 * my match...). Screens call the actions and listen to {@link ClientListener} callbacks; they never see JSON.
 *
 * <p><b>Threading.</b> All state lives on the UI executor (the Swing thread in the app): the connection
 * delivers every message there, and every returned future completes there. Only connecting, which blocks,
 * happens on a background thread.
 *
 * <p><b>Reconnecting.</b> If the connection drops while logged in (not after a logout or a kick), the client
 * reconnects with its token (AUTH) every 2 s for up to 30 s, the server's reconnect grace period
 * (docs/rules.md, section 4). The server then resends the room and the match state.
 */
public final class GameClient {

    static final long RECONNECT_INTERVAL_MS = 2_000;
    static final long RECONNECT_WINDOW_MS = 30_000;
    static final int CHAT_KEEP = 100;

    private final Executor ui;
    private final ScheduledExecutorService background = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "caro-background");
        t.setDaemon(true);
        return t;
    });
    private final List<ClientListener> listeners = new CopyOnWriteArrayList<>();

    // ---- UI thread only ----
    private String host;
    private int port;
    private Connection connection;
    private int connectionGeneration; // events of an older connection are ignored
    private String token;
    private UserInfo me;
    private List<RoomInfo> rooms = List.of();
    private List<OnlineUser> online = List.of();
    private RoomInfo room; // the room I play in, or the room I watch
    private MatchView match;
    private List<ChatMessage> chat = List.of(); // messages of the current room since I entered it
    private QueueStatus queue;
    private boolean loggingOut;
    private String kickReason;
    private long reconnectUntil; // 0 = not reconnecting

    /** @param ui where state changes and callbacks run: {@code SwingUtilities::invokeLater} in the app */
    public GameClient(Executor ui) {
        this.ui = ui;
    }

    public void addListener(ClientListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ClientListener listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------------ account

    public CompletableFuture<UserInfo> login(String host, int port, String username, String password) {
        return connect(host, port)
                .thenCompose(c -> c.request("LOGIN", Map.of("username", username, "password", password)))
                .thenApply(data -> {
                    token = data.path("token").asText();
                    return loggedIn(Json.convert(data.path("user"), UserInfo.class));
                })
                .whenComplete((user, error) -> {
                    if (error != null) {
                        dropConnection();
                    }
                });
    }

    /** Creates the account, then logs in with it. */
    public CompletableFuture<UserInfo> register(String host, int port, String username, String password,
                                                String displayName) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("password", password);
        if (displayName != null && !displayName.isBlank()) {
            data.put("displayName", displayName.trim());
        }
        return connect(host, port)
                .thenCompose(c -> c.request("REGISTER", data))
                .thenCompose(ignored -> login(host, port, username, password))
                .whenComplete((user, error) -> {
                    if (error != null) {
                        dropConnection();
                    }
                });
    }

    /** The server leaves the room for us (forfeiting a running match), then closes the connection. */
    public void logout() {
        loggingOut = true;
        if (connection == null) {
            reset();
            fire(l -> l.loggedOut(null));
            return;
        }
        Connection c = connection;
        request("LOGOUT", Map.of()).whenComplete((ok, error) -> c.close("Đã đăng xuất"));
    }

    /** Closes everything when the window closes. */
    public void shutdown() {
        loggingOut = true;
        if (connection != null) {
            connection.close("Đã thoát");
        }
        background.shutdownNow();
    }

    // ------------------------------------------------------------------ rooms and lobby

    public CompletableFuture<RoomInfo> createRoom(String name) {
        return createRoom(name, false);
    }

    /** @param blockTwoEnds the optional rule of docs/rules.md, section 9 */
    public CompletableFuture<RoomInfo> createRoom(String name, boolean blockTwoEnds) {
        Map<String, Object> data = new HashMap<>();
        if (name != null && !name.isBlank()) {
            data.put("name", name.trim());
        }
        data.put("blockTwoEnds", blockTwoEnds);
        return request("CREATE_ROOM", data).thenApply(this::roomFromReply);
    }

    /** Watch a room. The server sends MATCH_STATE (if there is a match) before the reply. */
    public CompletableFuture<RoomInfo> spectate(String roomId) {
        return request("SPECTATE", Map.of("roomId", roomId)).thenApply(this::roomFromReply);
    }

    /** @param level EASY or HARD */
    public CompletableFuture<RoomInfo> playBot(String level, boolean blockTwoEnds) {
        return request("PLAY_BOT", Map.of("level", level, "blockTwoEnds", blockTwoEnds)).thenApply(this::roomFromReply);
    }

    public CompletableFuture<Void> sendChat(String text) {
        return request("CHAT", Map.of("text", text)).thenAccept(ok -> { });
    }

    public CompletableFuture<RoomInfo> joinRoom(String roomId) {
        return request("JOIN_ROOM", Map.of("roomId", roomId)).thenApply(this::roomFromReply);
    }

    /** Leaves my room, or stops watching. */
    public CompletableFuture<Void> leaveRoom() {
        return request("LEAVE_ROOM", Map.of()).thenAccept(ok -> {
            setRoom(null);
            setMatch(null);
        });
    }

    public CompletableFuture<QueueStatus> quickMatch() {
        return request("QUICK_MATCH", Map.of()).thenApply(data -> {
            QueueStatus status = Json.convert(data, QueueStatus.class);
            setQueue(status);
            return status;
        });
    }

    public CompletableFuture<Void> cancelQuickMatch() {
        return request("CANCEL_QUICK_MATCH", Map.of())
                .thenAccept(ok -> setQueue(new QueueStatus(false, null, null, 0)));
    }

    /** @return the invitation id */
    public CompletableFuture<String> invite(long userId) {
        return request("INVITE", Map.of("userId", userId)).thenApply(data -> data.path("inviteId").asText());
    }

    public CompletableFuture<RoomInfo> acceptInvite(String inviteId) {
        return request("INVITE_REPLY", Map.of("inviteId", inviteId, "accept", true)).thenApply(this::roomFromReply);
    }

    public CompletableFuture<Void> declineInvite(String inviteId) {
        return request("INVITE_REPLY", Map.of("inviteId", inviteId, "accept", false)).thenAccept(ok -> { });
    }

    public CompletableFuture<Void> cancelInvite(String inviteId) {
        return request("CANCEL_INVITE", Map.of("inviteId", inviteId)).thenAccept(ok -> { });
    }

    public CompletableFuture<RoomInfo> rematch() {
        return request("REMATCH", Map.of()).thenApply(this::roomFromReply);
    }

    // ------------------------------------------------------------------ match

    /** The board only changes when MOVE_APPLIED arrives, never before. */
    public CompletableFuture<Void> move(int x, int y) {
        if (match == null) {
            return CompletableFuture.failedFuture(new ServerException("MATCH_NOT_FOUND", "Không có trận đấu"));
        }
        return request("MOVE", Map.of("matchId", match.matchId(), "x", x, "y", y)).thenAccept(ok -> { });
    }

    public CompletableFuture<Void> resign() {
        if (match == null) {
            return CompletableFuture.failedFuture(new ServerException("MATCH_NOT_FOUND", "Không có trận đấu"));
        }
        return request("RESIGN", Map.of("matchId", match.matchId())).thenAccept(ok -> { });
    }

    // ------------------------------------------------------------------ stored data

    /** @param userId null for myself */
    public CompletableFuture<Profile> profile(Long userId) {
        return request("GET_PROFILE", userId == null ? Map.of() : Map.of("userId", userId))
                .thenApply(data -> Json.convert(data, Profile.class));
    }

    public CompletableFuture<Page<HistoryItem>> history(Long userId, int page, int size) {
        Map<String, Object> data = new HashMap<>();
        if (userId != null) {
            data.put("userId", userId);
        }
        data.put("page", page);
        data.put("size", size);
        return request("GET_HISTORY", data)
                .thenApply(node -> Json.convert(node, new TypeReference<Page<HistoryItem>>() { }));
    }

    public CompletableFuture<MatchDetail> matchDetail(String matchId) {
        return request("GET_MATCH", Map.of("matchId", matchId))
                .thenApply(data -> Json.convert(data, MatchDetail.class));
    }

    public CompletableFuture<Page<LeaderboardEntry>> leaderboard(int page, int size) {
        return request("GET_LEADERBOARD", Map.of("page", page, "size", size))
                .thenApply(node -> Json.convert(node, new TypeReference<Page<LeaderboardEntry>>() { }));
    }

    // ------------------------------------------------------------------ state

    public UserInfo me() {
        return me;
    }

    public List<RoomInfo> rooms() {
        return rooms;
    }

    public List<OnlineUser> online() {
        return online;
    }

    public RoomInfo room() {
        return room;
    }

    public MatchView match() {
        return match;
    }

    /** True when I only watch the room (docs/rules.md, section 10). */
    public boolean isSpectating() {
        return room != null && me != null && room.hasSpectator(me.id());
    }

    public List<ChatMessage> chat() {
        return chat;
    }

    public QueueStatus queue() {
        return queue;
    }

    public boolean isInQueue() {
        return queue != null && queue.inQueue();
    }

    /** Current time on the server clock (for countdowns). */
    public long serverNow() {
        return connection != null ? connection.serverNow() : System.currentTimeMillis();
    }

    public String serverAddress() {
        return host + ":" + port;
    }

    /** Drops the connection as if the network failed (tests): the client should reconnect on its own. */
    void simulateNetworkFailure() {
        if (connection != null) {
            connection.close("Mất kết nối tới server");
        }
    }

    // ------------------------------------------------------------------ connection handling

    private CompletableFuture<Connection> connect(String host, int port) {
        if (connection != null && connection.isOpen() && host.equals(this.host) && port == this.port) {
            return CompletableFuture.completedFuture(connection);
        }
        dropConnection();
        this.host = host;
        this.port = port;
        fire(l -> l.connectionChanged(ConnectionStatus.CONNECTING, "Đang kết nối tới " + host + ":" + port));
        CompletableFuture<Connection> result = new CompletableFuture<>();
        int generation = ++connectionGeneration;
        background.execute(() -> {
            try {
                Connection c = Connection.open(host, port, ui, new Events(generation));
                ui.execute(() -> {
                    if (generation != connectionGeneration) {
                        c.close("Bỏ kết nối cũ");
                        result.completeExceptionally(disconnected());
                        return;
                    }
                    connection = c;
                    fire(l -> l.connectionChanged(ConnectionStatus.CONNECTED, "Đã kết nối " + serverAddress()));
                    result.complete(c);
                });
            } catch (IOException e) {
                ui.execute(() -> {
                    fire(l -> l.connectionChanged(ConnectionStatus.DISCONNECTED, "Không kết nối được"));
                    result.completeExceptionally(new ServerException(ServerException.DISCONNECTED,
                            "Không kết nối được tới server " + host + ":" + port));
                });
            }
        });
        return result;
    }

    /** Receives the messages of one connection. Anything from an older connection is ignored. */
    private final class Events implements Connection.Listener {
        private final int generation;

        Events(int generation) {
            this.generation = generation;
        }

        @Override
        public void onEvent(String type, JsonNode data) {
            if (generation == connectionGeneration) {
                handleEvent(type, data);
            }
        }

        @Override
        public void onClosed(String reason) {
            if (generation == connectionGeneration) {
                connectionClosed(reason);
            }
        }
    }

    private void connectionClosed(String reason) {
        connection = null;
        if (me == null) {
            fire(l -> l.connectionChanged(ConnectionStatus.DISCONNECTED, reason));
            return;
        }
        if (loggingOut || kickReason != null) {
            String why = kickReason;
            reset();
            fire(l -> l.connectionChanged(ConnectionStatus.DISCONNECTED, reason));
            fire(l -> l.loggedOut(why));
            return;
        }
        if (reconnectUntil == 0) {
            reconnectUntil = System.currentTimeMillis() + RECONNECT_WINDOW_MS;
            fire(l -> l.notice(reason + ". Đang thử kết nối lại..."));
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (System.currentTimeMillis() > reconnectUntil) {
            reset();
            fire(l -> l.connectionChanged(ConnectionStatus.DISCONNECTED, "Mất kết nối"));
            fire(l -> l.loggedOut("Không thể kết nối lại tới server"));
            return;
        }
        long secondsLeft = Math.max(0, (reconnectUntil - System.currentTimeMillis()) / 1000);
        fire(l -> l.connectionChanged(ConnectionStatus.RECONNECTING,
                "Mất kết nối, đang kết nối lại (còn " + secondsLeft + " giây)"));
        int generation = ++connectionGeneration;
        background.schedule(() -> {
            try {
                Connection c = Connection.open(host, port, ui, new Events(generation));
                ui.execute(() -> resumeSession(c, generation));
            } catch (IOException e) {
                ui.execute(() -> {
                    if (generation == connectionGeneration && me != null) {
                        scheduleReconnect();
                    }
                });
            }
        }, RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** New connection after a drop: authenticate with the token; the server resends room and match. */
    private void resumeSession(Connection c, int generation) {
        if (generation != connectionGeneration || me == null) {
            c.close("Bỏ kết nối cũ");
            return;
        }
        connection = c;
        c.request("AUTH", Map.of("token", token)).whenComplete((data, error) -> {
            if (error == null) {
                reconnectUntil = 0;
                loggedIn(Json.convert(data.path("user"), UserInfo.class));
                fire(l -> l.notice("Đã kết nối lại"));
                return;
            }
            String code = ServerException.codeOf(error);
            if ("UNAUTHORIZED".equals(code) || "ACCOUNT_LOCKED".equals(code)) {
                kickReason = ServerException.messageOf(error);
            }
            c.close(ServerException.messageOf(error)); // -> connectionClosed: retry or log out
        });
    }

    private UserInfo loggedIn(UserInfo user) {
        me = user;
        loggingOut = false;
        kickReason = null;
        fire(l -> l.connectionChanged(ConnectionStatus.CONNECTED, "Đã kết nối " + serverAddress()));
        fire(l -> l.loggedIn(user));
        synchronizeState();
        return user;
    }

    /** Fetches the room list, the online list and my room/match/queue (also after a reconnect). */
    private void synchronizeState() {
        request("LIST_ROOMS", Map.of()).thenAccept(data -> setRooms(data.path("rooms")));
        request("LIST_ONLINE", Map.of()).thenAccept(data -> setOnline(data.path("users")));
        request("GET_STATE", Map.of()).thenAccept(data -> {
            JsonNode roomNode = data.path("room");
            JsonNode matchNode = data.path("match");
            setRoom(roomNode.isObject() ? Json.convert(roomNode, RoomInfo.class) : null);
            setMatch(matchNode.isObject() ? MatchView.of(Json.convert(matchNode, MatchState.class)) : null);
            setQueue(Json.convert(data.path("queue"), QueueStatus.class));
        });
    }

    private void handleEvent(String type, JsonNode data) {
        switch (type) {
            case "ROOM_LIST" -> setRooms(data.path("rooms"));
            case "ONLINE_USERS" -> setOnline(data.path("users"));
            case "ROOM_UPDATE" -> setRoom(Json.convert(data.path("room"), RoomInfo.class));
            case "MATCH_START", "MATCH_STATE" -> setMatch(MatchView.of(Json.convert(data, MatchState.class)));
            case "MOVE_APPLIED" -> {
                MoveApplied move = Json.convert(data, MoveApplied.class);
                if (isCurrentMatch(move.matchId())) {
                    match.apply(move);
                    fireMatch();
                }
            }
            case "MATCH_END" -> matchEnded(Json.convert(data, MatchResult.class));
            case "OPPONENT_DISCONNECTED" -> {
                if (isCurrentMatch(data.path("matchId").asText())) {
                    match.opponentDisconnected(data.path("graceDeadline").asLong(), serverNow());
                    fireMatch();
                    String text = match.isSpectating() ? "Một người chơi mất kết nối. Trận tạm dừng."
                            : "Đối thủ mất kết nối. Trận tạm dừng trong lúc chờ đối thủ quay lại.";
                    fire(l -> l.notice(text));
                }
            }
            case "OPPONENT_RECONNECTED" -> {
                if (isCurrentMatch(data.path("matchId").asText())) {
                    match.opponentReconnected(data.path("turnDeadline").asLong());
                    fireMatch();
                    String text = match.isSpectating() ? "Người chơi đã kết nối lại."
                            : "Đối thủ đã kết nối lại. Trận tiếp tục.";
                    fire(l -> l.notice(text));
                }
            }
            case "CHAT_MESSAGE" -> {
                ChatMessage message = Json.convert(data, ChatMessage.class);
                if (room != null && room.id().equals(message.roomId())) {
                    List<ChatMessage> next = new ArrayList<>(chat);
                    next.add(message);
                    setChat(next.size() > CHAT_KEEP ? next.subList(next.size() - CHAT_KEEP, next.size()) : next);
                }
            }
            case "SPECTATE_ENDED" -> {
                if (room != null && room.id().equals(data.path("roomId").asText())) {
                    setRoom(null);
                    setMatch(null);
                    fire(l -> l.notice("Phòng bạn đang xem đã đóng."));
                }
            }
            case "QUEUE_STATUS" -> setQueue(Json.convert(data, QueueStatus.class));
            case "INVITED" -> {
                Invitation invitation = Json.convert(data, Invitation.class);
                fire(l -> l.invited(invitation));
            }
            case "INVITE_CLOSED" -> {
                String id = data.path("inviteId").asText();
                String reason = data.path("reason").asText();
                fire(l -> l.inviteClosed(id, reason));
            }
            case "KICKED" -> kickReason = "LOGGED_IN_ELSEWHERE".equals(data.path("reason").asText())
                    ? "Tài khoản của bạn vừa đăng nhập ở nơi khác"
                    : "Tài khoản của bạn đã bị khóa";
            case "ERROR" -> fire(l -> l.notice("Lỗi: " + data.path("message").asText()));
            default -> { } // unknown event from a newer server: ignore
        }
    }

    private void matchEnded(MatchResult result) {
        if (!isCurrentMatch(result.matchId())) {
            return;
        }
        match.finish(result);
        int change = match.isSpectating() ? 0 : result.ratingChangeOf(match.myMark());
        if (change != 0 && me != null) {
            me = me.withRating(me.rating() + change);
            UserInfo updated = me;
            fire(l -> l.meChanged(updated));
        }
        fireMatch();
    }

    private boolean isCurrentMatch(String matchId) {
        return match != null && match.matchId().equals(matchId);
    }

    private RoomInfo roomFromReply(JsonNode data) {
        RoomInfo info = Json.convert(data.path("room"), RoomInfo.class);
        setRoom(info);
        return info;
    }

    private void setRooms(JsonNode node) {
        rooms = Json.convert(node, new TypeReference<List<RoomInfo>>() { });
        List<RoomInfo> snapshot = rooms;
        fire(l -> l.roomsChanged(snapshot));
    }

    private void setOnline(JsonNode node) {
        online = Json.convert(node, new TypeReference<List<OnlineUser>>() { });
        List<OnlineUser> snapshot = online;
        fire(l -> l.onlineChanged(snapshot));
    }

    /** Entering another room drops the match and the chat of the previous one. */
    private void setRoom(RoomInfo newRoom) {
        if (newRoom != null && match != null && !newRoom.id().equals(match.roomId())) {
            setMatch(null); // match of another room
        }
        boolean sameRoom = newRoom != null && room != null && newRoom.id().equals(room.id());
        room = newRoom;
        if (!sameRoom && !chat.isEmpty()) {
            setChat(List.of());
        }
        fire(l -> l.roomChanged(newRoom));
    }

    private void setChat(List<ChatMessage> messages) {
        chat = List.copyOf(messages);
        List<ChatMessage> snapshot = chat;
        fire(l -> l.chatChanged(snapshot));
    }

    private void setMatch(MatchView newMatch) {
        match = newMatch;
        fireMatch();
    }

    private void setQueue(QueueStatus status) {
        queue = status;
        fire(l -> l.queueChanged(status));
    }

    private void fireMatch() {
        MatchView current = match;
        fire(l -> l.matchChanged(current));
    }

    private CompletableFuture<JsonNode> request(String type, Object data) {
        if (connection == null) {
            return CompletableFuture.failedFuture(disconnected());
        }
        return connection.request(type, data);
    }

    /** Closes the current connection without triggering a reconnect. */
    private void dropConnection() {
        connectionGeneration++;
        if (connection != null) {
            connection.close("Bỏ kết nối");
            connection = null;
        }
    }

    private void reset() {
        dropConnection();
        token = null;
        me = null;
        rooms = List.of();
        online = List.of();
        room = null;
        match = null;
        chat = List.of();
        queue = null;
        loggingOut = false;
        kickReason = null;
        reconnectUntil = 0;
    }

    private void fire(Consumer<ClientListener> event) {
        for (ClientListener l : listeners) {
            event.accept(l);
        }
    }

    private static ServerException disconnected() {
        return new ServerException(ServerException.DISCONNECTED, "Chưa kết nối tới server");
    }
}
