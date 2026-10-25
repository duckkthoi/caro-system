package com.caro.server.lobby;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.caro.server.bot.BotLevel;
import com.caro.server.bot.BotPlayer;
import com.caro.server.common.AppException;
import com.caro.server.common.ErrorCode;
import com.caro.server.config.CaroProperties;
import com.caro.server.match.GameMatch;
import com.caro.server.match.MatchEndListener;
import com.caro.server.match.MatchPersistenceService;
import com.caro.server.match.MatchResult;
import com.caro.server.match.MatchRuntime;
import com.caro.server.match.MatchState;
import com.caro.server.net.ServerMessageType;
import com.caro.server.net.SessionRegistry;
import com.caro.server.user.PlayerInfo;
import com.caro.server.user.UserRepository;

import jakarta.annotation.PostConstruct;

/**
 * Everything that happens outside a match: rooms, spectators, chat, the online list, quick match, invitations,
 * rematches and matches against the bot. Also routes match actions to the right {@link GameMatch}.
 *
 * <p><b>Locking.</b> All lobby state (rooms, online players, queue, invitations) is guarded by one lobby lock,
 * so a player can never end up in two rooms, or be matched and invited into two matches at once.
 * A thread holding the lobby lock may call into a match (which takes the match lock), but never the other way
 * round: a match calls back {@link #onMatchEnded} only after releasing its own lock. This fixed order
 * (lobby, then match) rules out deadlocks. Moves only take the lobby lock briefly to find the match, so
 * matches in different rooms run in parallel.
 *
 * <p><b>Structure.</b> Private helpers change state and notify the players directly involved. Each public
 * method then calls {@link #broadcastLobby} once, so everyone's room list and online list stay current.
 */
@Service
public class LobbyService implements MatchEndListener {

    private static final Logger log = LoggerFactory.getLogger(LobbyService.class);
    private static final int ROOM_NAME_MAX = 40;
    private static final int CHAT_MAX = 200;

    private final Object lock = new Object();
    // ---- guarded by lock ----
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<Long, Room> roomByUser = new HashMap<>();   // players only
    private final Map<Long, Room> spectating = new HashMap<>();   // spectators only
    private final Map<Long, PlayerInfo> online = new HashMap<>();
    private final Map<String, Invitation> invites = new LinkedHashMap<>();
    private final MatchmakingQueue queue;
    // ----
    private final AtomicLong roomSeq = new AtomicLong();
    private final AtomicLong inviteSeq = new AtomicLong();

    private final SessionRegistry sessions;
    private final UserRepository users;
    private final MatchPersistenceService persistence;
    private final MatchRuntime matchRuntime;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    private final CaroProperties props;

    public LobbyService(SessionRegistry sessions, UserRepository users, MatchPersistenceService persistence,
                        ScheduledExecutorService gameScheduler, Clock clock, CaroProperties props) {
        this.sessions = sessions;
        this.users = users;
        this.persistence = persistence;
        this.scheduler = gameScheduler;
        this.clock = clock;
        this.props = props;
        this.matchRuntime = new MatchRuntime(props.game(), sessions, gameScheduler, this, clock);
        this.queue = new MatchmakingQueue(props.matchmaking());
    }

    @PostConstruct
    void startMatchmaker() {
        long interval = props.matchmaking().checkInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::matchmakingTick, interval, interval, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------ rooms

    public List<RoomInfo> listRooms() {
        synchronized (lock) {
            return roomInfos();
        }
    }

    public int roomCount() {
        synchronized (lock) {
            return rooms.size();
        }
    }

    public int activeMatchCount() {
        synchronized (lock) {
            return (int) rooms.values().stream().filter(r -> r.status() == RoomStatus.PLAYING).count();
        }
    }

    public RoomInfo createRoom(long userId, String rawName, boolean blockTwoEnds) {
        PlayerInfo me = loadPlayer(userId);
        String name = rawName == null || rawName.isBlank() ? defaultRoomName(me) : rawName.trim();
        if (name.length() > ROOM_NAME_MAX) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên phòng tối đa 40 ký tự");
        }
        synchronized (lock) {
            requireNotInRoom(userId);
            dropFromQueue(userId);
            Room room = openRoom(me, name, blockTwoEnds, null);
            broadcastLobby();
            return room.info();
        }
    }

    /** Play against the computer: a room is opened with the bot as the second player, the user plays X first. */
    public RoomInfo playBot(long userId, BotLevel level, boolean blockTwoEnds) {
        PlayerInfo me = loadPlayer(userId);
        synchronized (lock) {
            requireNotInRoom(userId);
            dropFromQueue(userId);
            Room room = openRoom(me, "Chơi với " + level.displayName().toLowerCase(), blockTwoEnds, level);
            room.addPlayer(BotPlayer.info(level));
            startMatch(room, userId, BotPlayer.ID);
            broadcastLobby();
            return room.info();
        }
    }

    /** Joining a waiting room starts the match right away. The host plays X. */
    public RoomInfo joinRoom(long userId, String roomId) {
        synchronized (lock) {
            requireNotInRoom(userId);
            Room room = rooms.get(roomId);
            if (room == null) {
                throw new AppException(ErrorCode.ROOM_NOT_FOUND, "Phòng không tồn tại");
            }
            if (room.status() != RoomStatus.WAITING) {
                throw new AppException(ErrorCode.ROOM_FULL, "Phòng đã đủ người");
            }
            dropFromQueue(userId);
            seatAndStart(room, userId);
            broadcastLobby();
            return room.info();
        }
    }

    /** A player leaves their room; a spectator stops watching. */
    public void leaveRoom(long userId) {
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            if (room != null) {
                removeFromRoom(room, userId);
            } else if (!stopSpectating(userId)) {
                throw new AppException(ErrorCode.NOT_IN_ROOM, "Bạn không ở trong phòng nào");
            }
            broadcastLobby();
        }
    }

    public void leaveRoomIfPresent(long userId) {
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            if (room != null) {
                removeFromRoom(room, userId);
                broadcastLobby();
            } else if (stopSpectating(userId)) {
                broadcastLobby();
            }
        }
    }

    /**
     * Snapshot for GET_STATE: my room as a player, or else the room I am watching. Values may be null, hence a
     * HashMap instead of Map.of.
     */
    public Map<String, Object> getState(long userId) {
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            boolean watching = room == null && spectating.containsKey(userId);
            if (watching) {
                room = spectating.get(userId);
            }
            Map<String, Object> state = new HashMap<>();
            state.put("room", room == null ? null : room.info());
            state.put("match", activeMatchSnapshot(room, userId));
            state.put("queue", queueStatus(userId));
            state.put("spectating", watching);
            return state;
        }
    }

    // ------------------------------------------------------------------ spectators (docs/rules.md, section 10)

    /**
     * Starts watching a room. The spectator gets ROOM_UPDATE, then (if there is a match to show) MATCH_STATE, then
     * every event of the match, like the players.
     */
    public RoomInfo spectate(long userId, String roomId) {
        synchronized (lock) {
            if (roomByUser.containsKey(userId)) {
                throw new AppException(ErrorCode.ALREADY_IN_ROOM, "Hãy rời phòng của bạn trước khi xem phòng khác");
            }
            Room room = rooms.get(roomId);
            if (room == null) {
                throw new AppException(ErrorCode.ROOM_NOT_FOUND, "Phòng không tồn tại");
            }
            if (spectating.get(userId) != room) {
                if (room.isFullOfSpectators()) {
                    throw new AppException(ErrorCode.ROOM_FULL, "Phòng đã đủ " + Room.MAX_SPECTATORS + " người xem");
                }
                stopSpectating(userId);
                room.addSpectator(player(userId));
                spectating.put(userId, room);
                sendRoomUpdate(room);
                log.info("User {} is watching room {}", userId, room.id());
            }
            if (room.match() != null && room.status() != RoomStatus.WAITING) {
                room.match().addSpectator(userId); // sends MATCH_STATE
            }
            broadcastLobby();
            return room.info();
        }
    }

    // ------------------------------------------------------------------ chat (docs/rules.md, section 11)

    /** Sends a message to everyone in the sender's room (players and spectators), the sender included. */
    public void chat(long userId, String rawText) {
        String text = rawText == null ? "" : rawText.replaceAll("\\p{Cntrl}", " ").strip();
        if (text.isEmpty() || text.length() > CHAT_MAX) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tin nhắn phải dài từ 1 đến " + CHAT_MAX + " ký tự");
        }
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            boolean spectator = room == null;
            if (spectator) {
                room = spectating.get(userId);
            }
            if (room == null) {
                throw new AppException(ErrorCode.NOT_IN_ROOM, "Bạn không ở trong phòng nào");
            }
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("roomId", room.id());
            message.put("from", player(userId));
            message.put("spectator", spectator);
            message.put("text", text);
            message.put("sentAt", now());
            for (long memberId : room.memberIds()) {
                sessions.sendToUser(memberId, ServerMessageType.CHAT_MESSAGE, message);
            }
        }
    }

    // ------------------------------------------------------------------ rematch

    /**
     * Asks for a rematch in a FINISHED room. When both players have asked, a new match starts with the marks
     * swapped. The request is visible to both players in {@link RoomInfo#rematchRequests()}.
     */
    public RoomInfo requestRematch(long userId) {
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            if (room == null) {
                throw new AppException(ErrorCode.NOT_IN_ROOM, "Bạn không ở trong phòng nào");
            }
            if (room.status() != RoomStatus.FINISHED) {
                throw new AppException(ErrorCode.REMATCH_NOT_AVAILABLE,
                        "Chỉ chơi lại được khi trận vừa kết thúc và phòng còn đủ 2 người");
            }
            if (room.botLevel() != null) {
                room.requestRematch(BotPlayer.ID); // the bot always wants another game
            }
            if (room.requestRematch(userId)) {
                GameMatch last = room.match();
                startMatch(room, last.playerO().id(), last.playerX().id());
                broadcastLobby();
            } else {
                sendRoomUpdate(room);
            }
            return room.info();
        }
    }

    // ------------------------------------------------------------------ online list

    public List<OnlineUser> listOnline() {
        synchronized (lock) {
            return onlineUsers();
        }
    }

    // ------------------------------------------------------------------ quick match

    /** Enters the queue. If a suitable opponent is already waiting, the match starts before this returns. */
    public QueueStatus joinQueue(long userId) {
        PlayerInfo me = loadPlayer(userId);
        synchronized (lock) {
            requireNotInRoom(userId);
            if (queue.contains(userId)) {
                throw new AppException(ErrorCode.ALREADY_IN_QUEUE, "Bạn đang trong hàng chờ");
            }
            Invitation outgoing = outgoingInvite(userId);
            if (outgoing != null) {
                closeInvite(outgoing, InviteCloseReason.CANCELLED, false, true);
            }
            queue.add(me, now());
            log.info("User {} joined the queue ({} waiting)", userId, queue.size());
            matchWaitingPlayers();
            broadcastLobby();
            return queueStatus(userId);
        }
    }

    public void leaveQueue(long userId) {
        synchronized (lock) {
            if (queue.remove(userId) == null) {
                throw new AppException(ErrorCode.NOT_IN_QUEUE, "Bạn không ở trong hàng chờ");
            }
            broadcastLobby();
        }
    }

    /** Runs every caro.matchmaking.check-interval on the game scheduler. */
    void matchmakingTick() {
        try {
            synchronized (lock) {
                if (matchWaitingPlayers()) {
                    broadcastLobby();
                }
                for (MatchmakingQueue.Entry e : queue.widened(now())) {
                    long id = e.player().id();
                    sessions.sendToUser(id, ServerMessageType.QUEUE_STATUS, queueStatus(id));
                }
            }
        } catch (RuntimeException e) {
            log.error("Matchmaking tick failed", e);
        }
    }

    // ------------------------------------------------------------------ invitations

    /**
     * Invites an online player. The inviter must be idle or alone in a waiting room; the invited player must not
     * be in a room. Only one outgoing invitation per player at a time. It expires after caro.invite.timeout.
     */
    public Map<String, Object> invite(long userId, long targetId) {
        synchronized (lock) {
            if (targetId == userId) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Không thể tự mời chính mình");
            }
            if (!online.containsKey(targetId)) {
                throw new AppException(ErrorCode.USER_OFFLINE, "Người chơi này không online");
            }
            Room myRoom = roomByUser.get(userId);
            if (myRoom != null && myRoom.status() != RoomStatus.WAITING) {
                throw new AppException(ErrorCode.ALREADY_IN_ROOM, "Hãy rời phòng hiện tại trước khi mời");
            }
            if (queue.contains(userId)) {
                throw new AppException(ErrorCode.ALREADY_IN_QUEUE, "Hãy hủy tìm trận trước khi mời");
            }
            if (roomByUser.containsKey(targetId)) {
                throw new AppException(ErrorCode.USER_BUSY, "Người chơi này đang ở trong phòng khác");
            }
            if (outgoingInvite(userId) != null) {
                throw new AppException(ErrorCode.INVITE_PENDING, "Bạn đang có một lời mời chờ trả lời");
            }

            long timeoutMs = props.invite().timeout().toMillis();
            PlayerInfo me = player(userId);
            Invitation invitation = new Invitation("I" + inviteSeq.incrementAndGet(), me, targetId, now() + timeoutMs);
            invites.put(invitation.id(), invitation);
            invitation.setExpiryTimer(scheduler.schedule(
                    () -> expireInvite(invitation.id()), timeoutMs, TimeUnit.MILLISECONDS));

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("inviteId", invitation.id());
            event.put("from", me);
            event.put("expiresAt", invitation.expiresAt());
            sessions.sendToUser(targetId, ServerMessageType.INVITED, event);
            log.info("User {} invited user {} ({})", userId, targetId, invitation.id());

            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("inviteId", invitation.id());
            reply.put("expiresAt", invitation.expiresAt());
            return reply;
        }
    }

    /** The invited player accepts: they join the inviter's waiting room, or a new room is opened for both. */
    public RoomInfo acceptInvite(long userId, String inviteId) {
        synchronized (lock) {
            Invitation invitation = requireInviteTo(userId, inviteId);
            requireNotInRoom(userId);
            long inviterId = invitation.fromId();
            Room room = roomByUser.get(inviterId);
            boolean inviterReady = online.containsKey(inviterId) && !queue.contains(inviterId)
                    && (room == null || room.status() == RoomStatus.WAITING);
            if (!inviterReady) {
                closeInvite(invitation, InviteCloseReason.UNAVAILABLE, true, false);
                throw new AppException(ErrorCode.INVITE_NOT_FOUND, "Người mời không còn sẵn sàng");
            }
            closeInvite(invitation, InviteCloseReason.ACCEPTED, true, false);
            dropFromQueue(userId);
            if (room == null) {
                PlayerInfo inviter = player(inviterId);
                room = openRoom(inviter, defaultRoomName(inviter), false, null);
            }
            seatAndStart(room, userId);
            broadcastLobby();
            return room.info();
        }
    }

    public void declineInvite(long userId, String inviteId) {
        synchronized (lock) {
            closeInvite(requireInviteTo(userId, inviteId), InviteCloseReason.DECLINED, true, false);
        }
    }

    public void cancelInvite(long userId, String inviteId) {
        synchronized (lock) {
            Invitation invitation = invites.get(inviteId);
            if (invitation == null || invitation.fromId() != userId) {
                throw new AppException(ErrorCode.INVITE_NOT_FOUND, "Lời mời không tồn tại hoặc đã hết hạn");
            }
            closeInvite(invitation, InviteCloseReason.CANCELLED, false, true);
        }
    }

    private void expireInvite(String inviteId) {
        try {
            synchronized (lock) {
                Invitation invitation = invites.get(inviteId);
                if (invitation != null) {
                    closeInvite(invitation, InviteCloseReason.EXPIRED, true, true);
                }
            }
        } catch (RuntimeException e) {
            log.error("Failed to expire invitation {}", inviteId, e);
        }
    }

    // ------------------------------------------------------------------ match actions

    public int move(long userId, String matchId, int x, int y) {
        // the match lock is taken outside the lobby lock, so moves in different rooms don't block each other
        return requireMatch(userId, matchId).move(userId, x, y);
    }

    public void resign(long userId, String matchId) {
        requireMatch(userId, matchId).resign(userId);
    }

    /** Persists the result (rated matches only), then removes players who are no longer online from the room. */
    @Override
    public void onMatchEnded(GameMatch match) {
        if (match.options().rated()) {
            try {
                persistence.save(match);
            } catch (RuntimeException e) {
                log.error("Failed to save match {}", match.id(), e);
            }
        }
        MatchResult result = match.result();
        synchronized (lock) {
            PlayerInfo x = withRating(match.playerX(), result.ratingChangeX());
            PlayerInfo o = withRating(match.playerO(), result.ratingChangeO());
            online.computeIfPresent(x.id(), (id, old) -> x);
            online.computeIfPresent(o.id(), (id, old) -> o);

            Room room = rooms.get(match.roomId());
            if (room != null && room.match() == match) {
                room.updatePlayer(x);
                room.updatePlayer(o);
                for (PlayerInfo p : room.players()) {
                    if (!BotPlayer.isBot(p.id()) && !sessions.isOnline(p.id())) {
                        room.removePlayer(p.id());
                        roomByUser.remove(p.id());
                    }
                }
                if (room.isEmpty()) {
                    deleteRoom(room);
                } else {
                    sendRoomUpdate(room);
                }
            }
            broadcastLobby();
        }
    }

    // ------------------------------------------------------------------ connection events

    /** After LOGIN/AUTH: appear online, resend the room and match state, and resume a paused match. */
    public void onUserConnected(long userId) {
        PlayerInfo me = loadPlayer(userId);
        synchronized (lock) {
            online.put(userId, me);
            Room room = roomByUser.get(userId);
            if (room != null) {
                sessions.sendToUser(userId, ServerMessageType.ROOM_UPDATE, Map.of("room", room.info()));
                if (room.match() != null && room.status() != RoomStatus.WAITING) {
                    room.match().playerReconnected(userId);
                }
            }
            broadcastLobby();
        }
    }

    /**
     * The player went offline: they leave the queue, their invitations are closed, and they leave their room.
     * A live match is the exception: it is paused and waits for the player to come back.
     */
    public void onUserDisconnected(long userId) {
        synchronized (lock) {
            if (sessions.isOnline(userId)) {
                return; // already back with a new session
            }
            online.remove(userId);
            queue.remove(userId);
            stopSpectating(userId);
            closeInvitesOf(userId, InviteCloseReason.UNAVAILABLE);
            Room room = roomByUser.get(userId);
            if (room != null) {
                GameMatch match = room.match();
                if (match != null && !match.isFinished()) {
                    match.playerDisconnected(userId);
                } else {
                    removeFromRoom(room, userId);
                }
            }
            broadcastLobby();
        }
    }

    // ------------------------------------------------------------------ internals (caller holds the lock)

    /** @param botLevel null for a normal room */
    private Room openRoom(PlayerInfo host, String name, boolean blockTwoEnds, BotLevel botLevel) {
        stopSpectating(host.id());
        Room room = new Room("R" + roomSeq.incrementAndGet(), name, host, blockTwoEnds, botLevel);
        rooms.put(room.id(), room);
        roomByUser.put(host.id(), room);
        log.info("User {} opened room {}", host.id(), room.id());
        return room;
    }

    /** Seats a second player in a waiting room and starts the match. The host plays X. */
    private void seatAndStart(Room room, long guestId) {
        seat(room, player(guestId));
        startMatch(room, room.host().id(), guestId);
    }

    /** Becoming a player ends watching another room. */
    private void seat(Room room, PlayerInfo player) {
        stopSpectating(player.id());
        room.addPlayer(player);
        roomByUser.put(player.id(), room);
    }

    /** @return false if the user was not watching any room */
    private boolean stopSpectating(long userId) {
        Room room = spectating.remove(userId);
        if (room == null) {
            return false;
        }
        room.removeSpectator(userId);
        if (room.match() != null) {
            room.match().removeSpectator(userId);
        }
        sendRoomUpdate(room);
        return true;
    }

    /** Removes a room that has no player left. Its spectators are told and sent back to the lobby. */
    private void deleteRoom(Room room) {
        rooms.remove(room.id());
        for (long spectatorId : room.spectatorIds()) {
            spectating.remove(spectatorId);
            sessions.sendToUser(spectatorId, ServerMessageType.SPECTATE_ENDED, Map.of("roomId", room.id()));
        }
        log.info("Room {} closed", room.id());
    }

    /**
     * Starts a new match in a room that has both players. Ratings are re-read from the database, because Elo is
     * computed from the ratings at match start. Both players' other invitations are closed.
     */
    private void startMatch(Room room, long xId, long oId) {
        PlayerInfo x = participant(room, xId);
        PlayerInfo o = participant(room, oId);
        room.updatePlayer(x);
        room.updatePlayer(o);
        online.computeIfPresent(xId, (id, old) -> x);
        online.computeIfPresent(oId, (id, old) -> o);
        closeInvitesOf(xId, InviteCloseReason.UNAVAILABLE);
        closeInvitesOf(oId, InviteCloseReason.UNAVAILABLE);

        MatchRuntime runtime = matchRuntime;
        BotPlayer bot = null;
        if (room.botLevel() != null) {
            bot = new BotPlayer(room.botLevel(), scheduler, props.bot().moveDelay().toMillis());
            runtime = matchRuntime.withNotifier(bot.wrap(sessions));
        }
        GameMatch match = new GameMatch(UUID.randomUUID(), room.id(), x, o, room.options(), room.spectatorIds(),
                runtime);
        if (bot != null) {
            bot.attach(match);
        }
        room.setMatch(match);
        sendRoomUpdate(room);
        match.start();
    }

    /** Pairs whoever can be paired now. Marks are drawn at random. @return true if a match was started */
    private boolean matchWaitingPlayers() {
        List<MatchmakingQueue.Pair> pairs = queue.takePairs(now());
        for (MatchmakingQueue.Pair pair : pairs) {
            boolean firstIsX = ThreadLocalRandom.current().nextBoolean();
            PlayerInfo x = (firstIsX ? pair.first() : pair.second()).player();
            PlayerInfo o = (firstIsX ? pair.second() : pair.first()).player();
            for (PlayerInfo p : List.of(x, o)) {
                sessions.sendToUser(p.id(), ServerMessageType.QUEUE_STATUS, QueueStatus.notQueued(queue.size()));
            }
            Room room = openRoom(x, "Ghép trận ngẫu nhiên", false, null);
            seat(room, o);
            log.info("Matched user {} with user {} (ratings {} / {})", x.id(), o.id(), x.rating(), o.rating());
            startMatch(room, x.id(), o.id());
        }
        return !pairs.isEmpty();
    }

    /** Leaving during a running match counts as resigning. */
    private void removeFromRoom(Room room, long userId) {
        GameMatch match = room.match();
        if (match != null) {
            match.forfeitIfRunning(userId);
        }
        room.removePlayer(userId);
        roomByUser.remove(userId);
        log.info("User {} left room {}", userId, room.id());
        if (room.isEmpty()) {
            deleteRoom(room);
        } else {
            sendRoomUpdate(room);
        }
    }

    /** Takes the player out of the queue (because they entered a room) and tells them. */
    private void dropFromQueue(long userId) {
        if (queue.remove(userId) != null) {
            sessions.sendToUser(userId, ServerMessageType.QUEUE_STATUS, QueueStatus.notQueued(queue.size()));
        }
    }

    private QueueStatus queueStatus(long userId) {
        MatchmakingQueue.Entry entry = queue.get(userId);
        if (entry == null) {
            return QueueStatus.notQueued(queue.size());
        }
        return new QueueStatus(true, entry.joinedAt(), queue.rangeOf(entry, now()), queue.size());
    }

    private Invitation requireInviteTo(long userId, String inviteId) {
        Invitation invitation = invites.get(inviteId);
        if (invitation == null || invitation.toId() != userId) {
            throw new AppException(ErrorCode.INVITE_NOT_FOUND, "Lời mời không tồn tại hoặc đã hết hạn");
        }
        return invitation;
    }

    private Invitation outgoingInvite(long userId) {
        for (Invitation invitation : invites.values()) {
            if (invitation.fromId() == userId) {
                return invitation;
            }
        }
        return null;
    }

    /** Removes the invitation and sends INVITE_CLOSED to the chosen sides (the side that acted gets an OK). */
    private void closeInvite(Invitation invitation, InviteCloseReason reason, boolean tellInviter,
                             boolean tellInvitee) {
        invites.remove(invitation.id());
        invitation.cancelTimer();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("inviteId", invitation.id());
        event.put("reason", reason);
        if (tellInviter) {
            sessions.sendToUser(invitation.fromId(), ServerMessageType.INVITE_CLOSED, event);
        }
        if (tellInvitee) {
            sessions.sendToUser(invitation.toId(), ServerMessageType.INVITE_CLOSED, event);
        }
    }

    /** Closes every invitation sent or received by this user, telling the other side. */
    private void closeInvitesOf(long userId, InviteCloseReason reason) {
        for (Invitation invitation : List.copyOf(invites.values())) {
            if (invitation.involves(userId)) {
                closeInvite(invitation, reason, invitation.fromId() != userId, invitation.toId() != userId);
            }
        }
    }

    private GameMatch requireMatch(long userId, String matchId) {
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            GameMatch match = room == null ? null : room.match();
            if (match == null || !match.id().toString().equals(matchId)) {
                throw new AppException(ErrorCode.MATCH_NOT_FOUND, "Không tìm thấy trận đấu");
            }
            return match;
        }
    }

    private MatchState activeMatchSnapshot(Room room, long userId) {
        if (room == null || room.match() == null || room.status() == RoomStatus.WAITING) {
            return null;
        }
        return room.match().snapshotFor(userId);
    }

    private void requireNotInRoom(long userId) {
        if (roomByUser.containsKey(userId)) {
            throw new AppException(ErrorCode.ALREADY_IN_ROOM, "Bạn đang ở trong một phòng khác");
        }
    }

    private PresenceStatus presenceOf(long userId) {
        if (queue.contains(userId)) {
            return PresenceStatus.IN_QUEUE;
        }
        Room room = roomByUser.get(userId);
        if (room == null) {
            return spectating.containsKey(userId) ? PresenceStatus.SPECTATING : PresenceStatus.IDLE;
        }
        return room.status() == RoomStatus.PLAYING ? PresenceStatus.PLAYING : PresenceStatus.IN_ROOM;
    }

    /** Info of an online player without a database round trip. */
    private PlayerInfo player(long userId) {
        PlayerInfo p = online.get(userId);
        return p != null ? p : loadPlayer(userId);
    }

    /** A player about to start a match: the bot, or a user with the rating re-read from the database. */
    private PlayerInfo participant(Room room, long userId) {
        return BotPlayer.isBot(userId) ? BotPlayer.info(room.botLevel()) : loadPlayer(userId);
    }

    private PlayerInfo loadPlayer(long userId) {
        return users.findById(userId)
                .map(PlayerInfo::from)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Tài khoản không tồn tại"));
    }

    private long now() {
        return clock.millis();
    }

    private static String defaultRoomName(PlayerInfo host) {
        return "Phòng của " + host.displayName();
    }

    private static PlayerInfo withRating(PlayerInfo p, int delta) {
        return new PlayerInfo(p.id(), p.username(), p.displayName(), p.rating() + delta);
    }

    private List<RoomInfo> roomInfos() {
        return rooms.values().stream().map(Room::info).toList();
    }

    private List<OnlineUser> onlineUsers() {
        return online.values().stream()
                .sorted(Comparator.comparing(PlayerInfo::username))
                .map(p -> OnlineUser.of(p, presenceOf(p.id())))
                .toList();
    }

    /** To the human players and the spectators. */
    private void sendRoomUpdate(Room room) {
        Map<String, Object> event = Map.of("room", room.info());
        for (long memberId : room.memberIds()) {
            sessions.sendToUser(memberId, ServerMessageType.ROOM_UPDATE, event);
        }
    }

    /** Room list and online list for everyone. Each JSON is built once (see SessionRegistry.broadcast). */
    private void broadcastLobby() {
        sessions.broadcast(ServerMessageType.ROOM_LIST, Map.of("rooms", roomInfos()));
        sessions.broadcast(ServerMessageType.ONLINE_USERS, Map.of("users", onlineUsers()));
    }
}
