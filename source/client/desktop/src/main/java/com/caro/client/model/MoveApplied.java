package com.caro.client.model;

/** MOVE_APPLIED event. nextTurn and turnDeadline are null when the move ended the match. */
public record MoveApplied(String matchId, int moveNo, int x, int y, String mark, String nextTurn, Long turnDeadline) {
}
