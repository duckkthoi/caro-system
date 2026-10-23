package com.caro.server.admin;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.caro.server.common.AppException;
import com.caro.server.common.ErrorCode;
import com.caro.server.common.PageResponse;
import com.caro.server.lobby.LobbyService;
import com.caro.server.match.MatchRecordRepository;
import com.caro.server.net.SessionRegistry;
import com.caro.server.user.Role;
import com.caro.server.user.User;
import com.caro.server.user.UserRepository;
import com.caro.server.user.UserStatus;

/** Account management and monitoring. Only reachable through /api/admin/**. */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final Sort OLDEST_FIRST = Sort.by("id");

    private final UserRepository users;
    private final MatchRecordRepository matches;
    private final SessionRegistry sessions;
    private final LobbyService lobby;
    private final Clock clock;

    public AdminService(UserRepository users, MatchRecordRepository matches, SessionRegistry sessions,
                        LobbyService lobby, Clock clock) {
        this.users = users;
        this.matches = matches;
        this.sessions = sessions;
        this.lobby = lobby;
        this.clock = clock;
    }

    /** @param query part of the username or display name; blank means everyone */
    public PageResponse<AdminUserView> listUsers(String query, int page, int size) {
        Pageable pageable = PageResponse.request(page, size, OLDEST_FIRST);
        Page<User> result = query == null || query.isBlank()
                ? users.findAll(pageable)
                : users.search(query.trim(), pageable);
        return PageResponse.of(result, this::view);
    }

    /**
     * Locks the account, then kicks its online session (docs/rules.md, section 5). The status is committed
     * before the kick, so the player cannot log straight back in. A running match is handled like a
     * disconnect: if the player does not come back within the grace period (they can't), they lose.
     */
    public AdminUserView lock(long userId) {
        User user = requireUser(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không thể khóa tài khoản admin");
        }
        users.updateStatus(userId, UserStatus.LOCKED);
        boolean kicked = sessions.kick(userId, "ACCOUNT_LOCKED");
        log.info("Locked user {} ({}){}", userId, user.getUsername(), kicked ? ", kicked online session" : "");
        return view(requireUser(userId));
    }

    public AdminUserView unlock(long userId) {
        requireUser(userId);
        users.updateStatus(userId, UserStatus.ACTIVE);
        log.info("Unlocked user {}", userId);
        return view(requireUser(userId));
    }

    public SystemStats stats() {
        return new SystemStats(
                users.count(),
                users.countByStatus(UserStatus.LOCKED),
                matches.count(),
                matches.countByEndedAtAfter(clock.instant().minus(Duration.ofHours(24))),
                sessions.onlineCount(),
                lobby.roomCount(),
                lobby.activeMatchCount());
    }

    private User requireUser(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private AdminUserView view(User user) {
        return AdminUserView.of(user, sessions.isOnline(user.getId()));
    }
}
