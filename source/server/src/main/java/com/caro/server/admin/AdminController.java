package com.caro.server.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.caro.server.common.PageResponse;
import com.caro.server.lobby.LobbyService;
import com.caro.server.lobby.RoomInfo;
import com.caro.server.player.MatchSummary;
import com.caro.server.player.PlayerQueryService;

/** Role ADMIN only: enforced for /api/admin/** in SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final PlayerQueryService queries;
    private final LobbyService lobby;

    public AdminController(AdminService admin, PlayerQueryService queries, LobbyService lobby) {
        this.admin = admin;
        this.queries = queries;
        this.lobby = lobby;
    }

    @GetMapping("/stats")
    public SystemStats stats() {
        return admin.stats();
    }

    @GetMapping("/users")
    public PageResponse<AdminUserView> users(@RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return admin.listUsers(q, page, size);
    }

    @PostMapping("/users/{id}/lock")
    public AdminUserView lock(@PathVariable long id) {
        return admin.lock(id);
    }

    @PostMapping("/users/{id}/unlock")
    public AdminUserView unlock(@PathVariable long id) {
        return admin.unlock(id);
    }

    @GetMapping("/rooms")
    public List<RoomInfo> rooms() {
        return lobby.listRooms();
    }

    @GetMapping("/matches")
    public PageResponse<MatchSummary> matches(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return queries.recentMatches(page, size);
    }
}
