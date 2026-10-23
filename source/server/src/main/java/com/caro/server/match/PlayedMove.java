package com.caro.server.match;

import java.time.Instant;

import com.caro.server.game.Mark;

public record PlayedMove(int moveNo, long playerId, int x, int y, Mark mark, Instant playedAt) {
}
