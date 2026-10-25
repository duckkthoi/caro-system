// Run with `npm test` (Node's built-in test runner, which runs TypeScript directly).
import { test } from 'node:test';
import assert from 'node:assert/strict';

import type { MatchState } from '../api/protocol';
import {
  applyMove,
  canPlay,
  finish,
  fromState,
  isMyTurn,
  myRatingChange,
  opponentDisconnected,
  opponentReconnected,
  positionAfter,
  turnTimeLeft,
} from './match.ts';

const empty = Array.from({ length: 15 }, () => '.'.repeat(15));

function start(yourMark: 'X' | 'O' | null): MatchState {
  return {
    matchId: 'm1',
    roomId: 'R1',
    boardSize: 15,
    blockTwoEnds: false,
    rated: true,
    playerX: { id: 1, username: 'an', displayName: 'An', rating: 1000 },
    playerO: { id: 2, username: 'binh', displayName: 'Bình', rating: 1000 },
    yourMark,
    status: 'IN_PROGRESS',
    turn: 'X',
    moveCount: 0,
    board: empty,
    lastMove: null,
    turnDeadline: 30_000,
    turnTimeLeftMs: 30_000,
    result: null,
  };
}

test('X moves first; a move fills the cell and passes the turn', () => {
  const x = fromState(start('X'));
  assert.ok(isMyTurn(x));
  assert.ok(canPlay(x, 7, 7));

  const after = applyMove(x, { matchId: 'm1', moveNo: 1, x: 7, y: 7, mark: 'X', nextTurn: 'O', turnDeadline: 61_000 });
  assert.equal(after.board[7][7], 'X');
  assert.equal(after.board[7].length, 15);
  assert.deepEqual(after.lastMove, [7, 7]);
  assert.equal(isMyTurn(after), false);
  assert.equal(canPlay(after, 8, 8), false);
  assert.equal(x.board[7][7], '.', 'the previous view is not modified');
});

test('an occupied cell cannot be played', () => {
  const o = applyMove(fromState(start('O')), {
    matchId: 'm1', moveNo: 1, x: 3, y: 4, mark: 'X', nextTurn: 'O', turnDeadline: 61_000,
  });
  assert.ok(isMyTurn(o));
  assert.equal(canPlay(o, 3, 4), false);
  assert.ok(canPlay(o, 4, 4));
});

test('pausing keeps the turn time left and resuming uses the new deadline', () => {
  const paused = opponentDisconnected(fromState(start('X')), 50_000, 20_000);
  assert.equal(paused.status, 'PAUSED');
  assert.equal(turnTimeLeft(paused, 25_000), 10_000, 'the clock is frozen while paused');
  assert.equal(isMyTurn(paused), false);

  const resumed = opponentReconnected(paused, 40_000);
  assert.equal(turnTimeLeft(resumed, 35_000), 5_000);
  assert.ok(isMyTurn(resumed));
});

test('a finished match has no turn', () => {
  const done = finish(fromState(start('X')), {
    matchId: 'm1', result: 'O_WIN', reason: 'RESIGN', winnerId: 2, winLine: [], ratingChange: { X: -16, O: 16 },
  });
  assert.equal(done.status, 'FINISHED');
  assert.equal(done.turn, null);
  assert.equal(turnTimeLeft(done, 0), 0);
});

test('replay positions', () => {
  const moves = [
    { x: 7, y: 7, mark: 'X' as const },
    { x: 8, y: 8, mark: 'O' as const },
  ];
  assert.equal(positionAfter(15, moves, 0)[7][7], '.');
  assert.equal(positionAfter(15, moves, 1)[7][7], 'X');
  assert.equal(positionAfter(15, moves, 1)[8][8], '.');
  assert.equal(positionAfter(15, moves, 2)[8][8], 'O');
});

test('a spectator never has the turn and gets no rating change', () => {
  const watching = fromState(start(null));
  assert.equal(isMyTurn(watching), false);
  assert.equal(canPlay(watching, 7, 7), false);
  const ended = finish(watching, {
    matchId: 'm1', result: 'X_WIN', reason: 'RESIGN', winnerId: 1, ratingChange: { X: 16, O: -16 },
  });
  assert.equal(myRatingChange(ended, ended.result!), 0);
  assert.equal(myRatingChange(fromState(start('O')), ended.result!), -16);
});
