package com.caro.server.lobby;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

/**
 * Rooms and who is in which room (UC07-UC09, UC12), plus routing of match actions to the right {@link GameMatch}.
 *
 * <p><b>Locking.</b> All room state is guarded by one lobby lock. A thread holding the lobby lock may call into a
 * match (which takes the match lock), but never the other way round: a match calls back
 * {@link #onMatchEnded} only after releasing its own lock. This fixed order (lobby, then match) rules out deadlocks.
 * Moves only take the lobby lock briefly to find the match, so matches in different rooms run in parallel.
 */
@Service
public class LobbyService implements MatchEndListener {

    private static final Logger log = LoggerFactory.getLogger(LobbyService.class);
    private static final int ROOM_NAME_MAX = 40;

    private final Object lock = new Object();
    private final Map<String, Room> rooms = new LinkedHashMap<>(); // guarded by lock
    private final Map<Long, Room> roomByUser = new HashMap<>();    // guarded by lock
    private final AtomicLong roomSeq = new AtomicLong();

    private final SessionRegistry sessions;
    private final UserRepository users;
    private final MatchPersistenceService persistence;
    private final MatchRuntime matchRuntime;

    public LobbyService(SessionRegistry sessions, UserRepository users, MatchPersistenceService persistence,
                        ScheduledExecutorService gameScheduler, Clock clock, CaroProperties props) {
        this.sessions = sessions;
        this.users = users;
        this.persistence = persistence;
        this.matchRuntime = new MatchRuntime(props.game(), sessions, gameScheduler, this, clock);
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

    public RoomInfo createRoom(long userId, String rawName) {
        PlayerInfo me = loadPlayer(userId);
        String name = rawName == null || rawName.isBlank() ? "Phòng của " + me.displayName() : rawName.trim();
        if (name.length() > ROOM_NAME_MAX) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên phòng tối đa 40 ký tự");
        }
        synchronized (lock) {
            requireNotInRoom(userId);
            Room room = new Room("R" + roomSeq.incrementAndGet(), name, me);
            rooms.put(room.id(), room);
            roomByUser.put(userId, room);
            broadcastRoomList();
            log.info("User {} created room {}", userId, room.id());
            return room.info();
        }
    }

    /** UC09 + UC13: joining a waiting room starts the match right away. The host plays X. */
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
            // fresh ratings from the DB: Elo is computed from the ratings at match start
            PlayerInfo host = loadPlayer(room.host().id());
            PlayerInfo guest = loadPlayer(userId);
            room.updatePlayer(host);
            room.addPlayer(guest);
            roomByUser.put(userId, room);

            GameMatch match = new GameMatch(UUID.randomUUID(), room.id(), host, guest, matchRuntime);
            room.setMatch(match);
            sendRoomUpdate(room);
            match.start();
            broadcastRoomList();
            return room.info();
        }
    }

    public void leaveRoom(long userId) {
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            if (room == null) {
                throw new AppException(ErrorCode.NOT_IN_ROOM, "Bạn không ở trong phòng nào");
            }
            removeFromRoom(room, userId);
        }
    }

    public void leaveRoomIfPresent(long userId) {
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            if (room != null) {
                removeFromRoom(room, userId);
            }
        }
    }

    /** Snapshot for GET_STATE. Values may be null, hence a HashMap instead of Map.of. */
    public Map<String, Object> getState(long userId) {
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            Map<String, Object> state = new HashMap<>();
            state.put("room", room == null ? null : room.info());
            state.put("match", activeMatchSnapshot(room, userId));
            return state;
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

    /** Persists the result, then removes players who are no longer online from the room. */
    @Override
    public void onMatchEnded(GameMatch match) {
        try {
            persistence.save(match);
        } catch (RuntimeException e) {
            log.error("Failed to save match {}", match.id(), e);
        }
        MatchResult result = match.result();
        synchronized (lock) {
            Room room = rooms.get(match.roomId());
            if (room == null || room.match() != match) {
                return;
            }
            room.updatePlayer(withRating(match.playerX(), result.ratingChangeX()));
            room.updatePlayer(withRating(match.playerO(), result.ratingChangeO()));
            for (PlayerInfo p : room.players()) {
                if (!sessions.isOnline(p.id())) {
                    room.removePlayer(p.id());
                    roomByUser.remove(p.id());
                }
            }
            if (room.isEmpty()) {
                rooms.remove(room.id());
            } else {
                sendRoomUpdate(room);
            }
            broadcastRoomList();
        }
    }

    // ------------------------------------------------------------------ connection events

    /** After LOGIN/AUTH: resend the room and match state, and resume a paused match (UC20). */
    public void onUserConnected(long userId) {
        synchronized (lock) {
            Room room = roomByUser.get(userId);
            if (room == null) {
                return;
            }
            sessions.sendToUser(userId, ServerMessageType.ROOM_UPDATE, Map.of("room", room.info()));
            if (room.match() != null && room.status() != RoomStatus.WAITING) {
                room.match().playerReconnected(userId);
            }
        }
    }

    /** A live match is paused and waits for the player; otherwise the player simply leaves the room. */
    public void onUserDisconnected(long userId) {
        synchronized (lock) {
            if (sessions.isOnline(userId)) {
                return; // already back with a new session
            }
            Room room = roomByUser.get(userId);
            if (room == null) {
                return;
            }
            GameMatch match = room.match();
            if (match != null && !match.isFinished()) {
                match.playerDisconnected(userId);
            } else {
                removeFromRoom(room, userId);
            }
        }
    }

    // ------------------------------------------------------------------ internals

    /** Caller holds the lock. Leaving during a running match counts as resigning. */
    private void removeFromRoom(Room room, long userId) {
        GameMatch match = room.match();
        if (match != null) {
            match.forfeitIfRunning(userId);
        }
        room.removePlayer(userId);
        roomByUser.remove(userId);
        if (room.isEmpty()) {
            rooms.remove(room.id());
        } else {
            sendRoomUpdate(room);
        }
        broadcastRoomList();
        log.info("User {} left room {}", userId, room.id());
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

    private PlayerInfo loadPlayer(long userId) {
        return users.findById(userId)
                .map(PlayerInfo::from)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Tài khoản không tồn tại"));
    }

    private static PlayerInfo withRating(PlayerInfo p, int delta) {
        return new PlayerInfo(p.id(), p.username(), p.displayName(), p.rating() + delta);
    }

    private List<RoomInfo> roomInfos() {
        return rooms.values().stream().map(Room::info).toList();
    }

    private void sendRoomUpdate(Room room) {
        RoomInfo info = room.info();
        for (PlayerInfo p : room.players()) {
            sessions.sendToUser(p.id(), ServerMessageType.ROOM_UPDATE, Map.of("room", info));
        }
    }

    private void broadcastRoomList() {
        sessions.broadcast(ServerMessageType.ROOM_LIST, Map.of("rooms", roomInfos()));
    }
}
