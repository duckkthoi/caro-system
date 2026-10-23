package com.caro.server.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.caro.server.lobby.LobbyService;
import com.caro.server.net.SessionRegistry;

@RestController
public class HealthController {

    private final SessionRegistry sessions;
    private final LobbyService lobby;

    public HealthController(SessionRegistry sessions, LobbyService lobby) {
        this.sessions = sessions;
        this.lobby = lobby;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "onlineUsers", sessions.onlineCount(), "rooms", lobby.roomCount());
    }
}
