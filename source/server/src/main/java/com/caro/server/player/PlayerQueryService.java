package com.caro.server.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.caro.server.common.AppException;
import com.caro.server.common.ErrorCode;
import com.caro.server.common.PageResponse;
import com.caro.server.config.CaroProperties;
import com.caro.server.game.Board;
import com.caro.server.game.Mark;
import com.caro.server.game.RuleEngine;
import com.caro.server.match.EndReason;
import com.caro.server.match.MatchRecord;
import com.caro.server.match.MatchRecordRepository;
import com.caro.server.match.MoveRecord;
import com.caro.server.net.SessionRegistry;
import com.caro.server.user.PlayerInfo;
import com.caro.server.user.Role;
import com.caro.server.user.User;
import com.caro.server.user.UserRepository;
import com.caro.server.user.UserStatus;

/**
 * Read-only queries on stored data: profile, statistics, match history, replay, leaderboard.
 * Shared by the REST API and the socket protocol, so the desktop client gets the same data as the web client.
 */
@Service
@Transactional(readOnly = true)
public class PlayerQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "endedAt");
    private static final Sort BEST_FIRST = Sort.by(Sort.Order.desc("rating"), Sort.Order.asc("id"));

    private final UserRepository users;
    private final MatchRecordRepository matches;
    private final SessionRegistry sessions;
    private final CaroProperties.Game game;

    public PlayerQueryService(UserRepository users, MatchRecordRepository matches, SessionRegistry sessions,
                              CaroProperties props) {
        this.users = users;
        this.matches = matches;
        this.sessions = sessions;
        this.game = props.game();
    }

    public Profile profile(long userId) {
        User user = requireUser(userId);
        return new Profile(PlayerInfo.from(user), sessions.isOnline(userId), rankOf(user), stats(userId),
                user.getCreatedAt());
    }

    public PlayerStats stats(long userId) {
        MatchRecordRepository.StatsRow row = matches.statsOf(userId);
        return PlayerStats.of(row.getPlayed(), row.getWins(), row.getDraws());
    }

    /** Matches of one player, newest first. */
    public PageResponse<HistoryItem> history(long userId, int page, int size) {
        requireUser(userId);
        Page<MatchRecord> result = matches.findByPlayer(userId, PageResponse.request(page, size, NEWEST_FIRST));
        Map<Long, PlayerInfo> players = playersOf(result.getContent());
        return PageResponse.of(result, m -> HistoryItem.of(m, userId, players));
    }

    /** All stored matches, newest first (admin). */
    public PageResponse<MatchSummary> recentMatches(int page, int size) {
        Page<MatchRecord> result = matches.findAll(PageResponse.request(page, size, NEWEST_FIRST));
        Map<Long, PlayerInfo> players = playersOf(result.getContent());
        return PageResponse.of(result, m -> MatchSummary.of(m, players));
    }

    public MatchDetail match(UUID matchId) {
        MatchRecord m = matches.findById(matchId)
                .orElseThrow(() -> new AppException(ErrorCode.MATCH_NOT_FOUND, "Không tìm thấy trận đấu"));
        List<MatchDetail.Move> moves = m.getMoves().stream()
                .map(mv -> new MatchDetail.Move(mv.getMoveNo(), markOf(m, mv), mv.getX(), mv.getY(), mv.getPlayedAt()))
                .toList();
        return new MatchDetail(MatchSummary.of(m, playersOf(List.of(m))), winLine(m), moves);
    }

    /** Active players by rating. Equal ratings share a rank, consistent with {@link #rankOf}. */
    public PageResponse<LeaderboardEntry> leaderboard(int page, int size) {
        Page<User> result = users.findByRoleAndStatus(Role.PLAYER, UserStatus.ACTIVE,
                PageResponse.request(page, size, BEST_FIRST));
        List<User> rows = result.getContent();
        List<LeaderboardEntry> entries = new ArrayList<>(rows.size());
        int rank = 0;
        for (int i = 0; i < rows.size(); i++) {
            User user = rows.get(i);
            if (i == 0) {
                rank = rankOf(user); // the page may start in the middle of a group of equal ratings
            } else if (user.getRating() != rows.get(i - 1).getRating()) {
                rank = (int) result.getPageable().getOffset() + i + 1;
            }
            entries.add(new LeaderboardEntry(rank, PlayerInfo.from(user), sessions.isOnline(user.getId())));
        }
        return PageResponse.of(result, entries);
    }

    private Integer rankOf(User user) {
        if (user.getRole() != Role.PLAYER || user.isLocked()) {
            return null;
        }
        return (int) users.countByRoleAndStatusAndRatingGreaterThan(Role.PLAYER, UserStatus.ACTIVE,
                user.getRating()) + 1;
    }

    /** The winning line is not stored: replay the moves and check the last one, like the live match did. */
    private List<int[]> winLine(MatchRecord m) {
        List<MoveRecord> moves = m.getMoves();
        if (m.getEndReason() != EndReason.FIVE_IN_ROW || moves.isEmpty()) {
            return null;
        }
        Board board = new Board(game.boardSize());
        for (MoveRecord mv : moves) {
            board.place(mv.getX(), mv.getY(), markOf(m, mv));
        }
        MoveRecord last = moves.get(moves.size() - 1);
        return RuleEngine.findWinLine(board, last.getX(), last.getY(), game.winLength(), m.isBlockTwoEnds());
    }

    private User requireUser(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người chơi"));
    }

    /** Loads both players of every match with one query. */
    private Map<Long, PlayerInfo> playersOf(Collection<MatchRecord> records) {
        Set<Long> ids = new HashSet<>();
        for (MatchRecord m : records) {
            ids.add(m.getPlayerXId());
            ids.add(m.getPlayerOId());
        }
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, PlayerInfo::from));
    }

    private static Mark markOf(MatchRecord m, MoveRecord mv) {
        return mv.getPlayerId() == m.getPlayerXId() ? Mark.X : Mark.O;
    }
}
