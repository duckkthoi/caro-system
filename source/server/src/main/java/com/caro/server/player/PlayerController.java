package com.caro.server.player;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.caro.server.common.PageResponse;

/** Public player data. Any logged-in user may read it. */
@RestController
@RequestMapping("/api")
public class PlayerController {

    private final PlayerQueryService queries;

    public PlayerController(PlayerQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/users/{id}")
    public Profile profile(@PathVariable long id) {
        return queries.profile(id);
    }

    @GetMapping("/users/{id}/matches")
    public PageResponse<HistoryItem> history(@PathVariable long id,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return queries.history(id, page, size);
    }

    @GetMapping("/matches/{id}")
    public MatchDetail match(@PathVariable UUID id) {
        return queries.match(id);
    }

    @GetMapping("/leaderboard")
    public PageResponse<LeaderboardEntry> leaderboard(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return queries.leaderboard(page, size);
    }
}
