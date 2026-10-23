// The client's copy of the current match. Pure functions: each server message produces a new MatchView.
// The client never decides a move or a result itself; it only mirrors what the server sends.
// Only type imports here, so `node --test` can run the tests without a bundler.
import type { Mark, MatchResult, MatchState, MatchStatus, MoveApplied, PlayerInfo } from '../api/protocol';

export interface MatchView {
  matchId: string;
  roomId: string;
  size: number;
  board: string[]; // rows y = 0..size-1, chars '.', 'X', 'O'
  playerX: PlayerInfo;
  playerO: PlayerInfo;
  myMark: Mark;
  status: MatchStatus;
  turn: Mark | null;
  turnDeadline: number | null; // server epoch ms, only while IN_PROGRESS
  pausedTimeLeftMs: number;
  graceDeadline: number | null; // while the opponent is disconnected
  moveCount: number;
  lastMove: [number, number] | null;
  result: MatchResult | null;
}

export function fromState(s: MatchState): MatchView {
  return {
    matchId: s.matchId,
    roomId: s.roomId,
    size: s.boardSize,
    board: s.board,
    playerX: s.playerX,
    playerO: s.playerO,
    myMark: s.yourMark,
    status: s.status,
    turn: s.turn,
    turnDeadline: s.turnDeadline,
    pausedTimeLeftMs: s.turnTimeLeftMs,
    graceDeadline: null,
    moveCount: s.moveCount,
    lastMove: s.lastMove ? [s.lastMove.x, s.lastMove.y] : null,
    result: s.result,
  };
}

export function applyMove(m: MatchView, move: MoveApplied): MatchView {
  const board = m.board.map((row, y) =>
    y === move.y ? row.slice(0, move.x) + move.mark + row.slice(move.x + 1) : row,
  );
  return {
    ...m,
    board,
    lastMove: [move.x, move.y],
    moveCount: move.moveNo,
    turn: move.nextTurn,
    turnDeadline: move.turnDeadline,
  };
}

export function finish(m: MatchView, result: MatchResult): MatchView {
  return { ...m, status: 'FINISHED', result, turn: null, turnDeadline: null, graceDeadline: null };
}

export function opponentDisconnected(m: MatchView, graceDeadline: number, serverNow: number): MatchView {
  const left = m.turnDeadline !== null ? Math.max(0, m.turnDeadline - serverNow) : m.pausedTimeLeftMs;
  return { ...m, status: 'PAUSED', turnDeadline: null, pausedTimeLeftMs: left, graceDeadline };
}

export function opponentReconnected(m: MatchView, turnDeadline: number): MatchView {
  return { ...m, status: 'IN_PROGRESS', turnDeadline, graceDeadline: null };
}

export function isMyTurn(m: MatchView): boolean {
  return m.status === 'IN_PROGRESS' && m.turn === m.myMark;
}

export function canPlay(m: MatchView, x: number, y: number): boolean {
  return isMyTurn(m) && m.board[y]?.[x] === '.';
}

export function turnTimeLeft(m: MatchView, serverNow: number): number {
  if (m.turnDeadline !== null) {
    return Math.max(0, m.turnDeadline - serverNow);
  }
  return m.status === 'PAUSED' ? m.pausedTimeLeftMs : 0;
}

export function opponentOf(m: MatchView): PlayerInfo {
  return m.myMark === 'X' ? m.playerO : m.playerX;
}

/** Board after the first `count` moves of a stored match (replay). */
export function positionAfter(size: number, moves: { x: number; y: number; mark: Mark }[], count: number): string[] {
  const rows = Array.from({ length: size }, () => '.'.repeat(size).split(''));
  for (const move of moves.slice(0, count)) {
    rows[move.y][move.x] = move.mark;
  }
  return rows.map((r) => r.join(''));
}
