package com.caro.server.match;

import java.util.UUID;

import com.caro.server.game.Mark;

/** Payload of MOVE_APPLIED. nextTurn and turnDeadline are null when this move ended the match. */
public record MoveApplied(UUID matchId, int moveNo, int x, int y, Mark mark, Mark nextTurn, Long turnDeadline) {
}
