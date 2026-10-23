// Data types of the protocol (docs/protocol.md, section 5). Same JSON over WebSocket and REST.

export type Mark = 'X' | 'O';

export interface UserInfo {
  id: number;
  username: string;
  displayName: string;
  role: 'PLAYER' | 'ADMIN';
  rating: number;
}

export interface PlayerInfo {
  id: number;
  username: string;
  displayName: string;
  rating: number;
}

export type RoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED';

export interface RoomInfo {
  id: string;
  name: string;
  status: RoomStatus;
  hostId: number;
  players: PlayerInfo[];
  matchId: string | null;
  rematchRequests: number[];
}

export type MatchStatus = 'IN_PROGRESS' | 'PAUSED' | 'FINISHED';

export interface MatchResult {
  matchId: string;
  result: 'X_WIN' | 'O_WIN' | 'DRAW' | 'ABORTED';
  reason: EndReason;
  winnerId: number | null;
  winLine?: [number, number][] | null;
  ratingChange: Record<Mark, number>;
}

export type EndReason = 'FIVE_IN_ROW' | 'BOARD_FULL' | 'RESIGN' | 'TIMEOUT' | 'DISCONNECT' | 'BOTH_DISCONNECTED';

export interface MatchState {
  matchId: string;
  roomId: string;
  boardSize: number;
  playerX: PlayerInfo;
  playerO: PlayerInfo;
  yourMark: Mark;
  status: MatchStatus;
  turn: Mark | null;
  moveCount: number;
  board: string[];
  lastMove: { x: number; y: number; mark: Mark } | null;
  turnDeadline: number | null;
  turnTimeLeftMs: number;
  result: MatchResult | null;
}

export interface MoveApplied {
  matchId: string;
  moveNo: number;
  x: number;
  y: number;
  mark: Mark;
  nextTurn: Mark | null;
  turnDeadline: number | null;
}

export type Presence = 'IDLE' | 'IN_QUEUE' | 'IN_ROOM' | 'PLAYING';

export interface OnlineUser {
  id: number;
  username: string;
  displayName: string;
  rating: number;
  status: Presence;
}

export interface QueueStatus {
  inQueue: boolean;
  since: number | null;
  ratingRange: number | null;
  queueSize: number;
}

export interface Invitation {
  inviteId: string;
  from: PlayerInfo;
  expiresAt: number;
}

export type InviteCloseReason = 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED' | 'UNAVAILABLE';

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Profile {
  player: PlayerInfo;
  online: boolean;
  rank: number | null;
  stats: { played: number; wins: number; losses: number; draws: number; winRate: number };
  joinedAt: string;
}

export interface HistoryItem {
  matchId: string;
  opponent: PlayerInfo;
  mark: Mark;
  outcome: 'WIN' | 'LOSS' | 'DRAW' | 'ABORTED';
  reason: EndReason;
  ratingChange: number;
  totalMoves: number;
  startedAt: string;
  endedAt: string;
}

export interface LeaderboardEntry {
  rank: number;
  player: PlayerInfo;
  online: boolean;
}

export interface MatchSummary {
  matchId: string;
  roomId: string;
  playerX: PlayerInfo;
  playerO: PlayerInfo;
  result: MatchResult['result'];
  reason: EndReason;
  winnerId: number | null;
  ratingChange: Record<Mark, number>;
  totalMoves: number;
  startedAt: string;
  endedAt: string;
}

export interface MatchDetail {
  match: MatchSummary;
  winLine: [number, number][] | null;
  moves: { moveNo: number; mark: Mark; x: number; y: number; playedAt: string }[];
}

export interface AdminUserView {
  id: number;
  username: string;
  displayName: string;
  role: 'PLAYER' | 'ADMIN';
  status: 'ACTIVE' | 'LOCKED';
  rating: number;
  createdAt: string;
  online: boolean;
}

export interface SystemStats {
  totalUsers: number;
  lockedUsers: number;
  totalMatches: number;
  matchesLast24h: number;
  onlineUsers: number;
  rooms: number;
  activeMatches: number;
}

/** A failed request: an ERROR reply / REST error body, or a local problem (DISCONNECTED, TIMEOUT). */
export class ServerError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
