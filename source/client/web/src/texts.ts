// Vietnamese labels for the codes sent by the server.
import type { BotLevel, EndReason, PlayerInfo, Presence, RoomStatus } from './api/protocol';

export const roomStatus: Record<RoomStatus, string> = {
  WAITING: 'Đang chờ',
  PLAYING: 'Đang đấu',
  FINISHED: 'Vừa đấu xong',
};

export const presence: Record<Presence, string> = {
  IDLE: 'Rảnh',
  IN_QUEUE: 'Đang tìm trận',
  IN_ROOM: 'Trong phòng',
  PLAYING: 'Đang đấu',
  SPECTATING: 'Đang xem',
};

export const botLevel: Record<BotLevel, string> = {
  EASY: 'Dễ',
  HARD: 'Khó',
};

export const BLOCK_TWO_ENDS = 'Luật chặn hai đầu';

/** Players who can be invited (docs/rules.md, sections 8.3 and 10). */
export function invitable(status: Presence): boolean {
  return status === 'IDLE' || status === 'IN_QUEUE' || status === 'SPECTATING';
}

export const outcome: Record<string, string> = {
  WIN: 'Thắng',
  LOSS: 'Thua',
  DRAW: 'Hòa',
  ABORTED: 'Bị hủy',
};

export const reason: Record<EndReason, string> = {
  FIVE_IN_ROW: '5 quân liên tiếp',
  BOARD_FULL: 'Kín bàn cờ',
  RESIGN: 'Đầu hàng',
  TIMEOUT: 'Hết giờ',
  DISCONNECT: 'Mất kết nối',
  BOTH_DISCONNECTED: 'Cả hai mất kết nối',
};

export const result: Record<string, string> = {
  X_WIN: 'X thắng',
  O_WIN: 'O thắng',
  DRAW: 'Hòa',
  ABORTED: 'Bị hủy',
};

export function signed(n: number): string {
  return n > 0 ? `+${n}` : String(n);
}

export function player(p: PlayerInfo | null | undefined): string {
  return p ? `${p.displayName} (${p.rating})` : '?';
}

/** m:ss, rounded up so that 0.2 s left still shows 0:01. */
export function clock(ms: number): string {
  const s = Math.ceil(Math.max(0, ms) / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** HH:mm of an epoch-ms time (chat). */
export function time(epochMs: number): string {
  return new Date(epochMs).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

export function dateTime(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}

export function date(iso: string): string {
  return new Date(iso).toLocaleDateString('vi-VN');
}
