import { useSyncExternalStore } from 'react';

import type {
  Invitation,
  MatchResult,
  MatchState,
  MoveApplied,
  OnlineUser,
  PlayerInfo,
  QueueStatus,
  RoomInfo,
  UserInfo,
} from '../api/protocol';
import { ServerError, errorMessage } from '../api/protocol';
import { rest } from '../api/rest';
import { GameSocket } from '../api/socket';
import * as M from './match';
import type { MatchView } from './match';

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

export interface Notice {
  id: number;
  text: string;
  kind: 'info' | 'error';
}

export interface State {
  connection: ConnectionStatus;
  token: string | null;
  me: UserInfo | null;
  rooms: RoomInfo[];
  online: OnlineUser[];
  room: RoomInfo | null;
  match: MatchView | null;
  queue: QueueStatus | null;
  invites: Invitation[]; // received, waiting for my answer
  outgoing: { inviteId: string; name: string } | null;
  notices: Notice[];
  logoutReason: string | null;
}

const TOKEN_KEY = 'caro.token'; // sessionStorage: one account per browser tab
const RECONNECT_INTERVAL_MS = 2_000;
const RECONNECT_WINDOW_MS = 30_000; // the server keeps a match waiting this long (docs/rules.md, section 4)
const NOTICE_MS = 6_000;

const initial: State = {
  connection: 'disconnected',
  token: null,
  me: null,
  rooms: [],
  online: [],
  room: null,
  match: null,
  queue: null,
  invites: [],
  outgoing: null,
  notices: [],
  logoutReason: null,
};

/**
 * The whole client state and every action. Components read the state with useStore() and call the actions;
 * they never touch the WebSocket or JSON.
 *
 * Login: REST /api/auth/login gives a JWT, then the WebSocket sends AUTH with it. If the socket drops while
 * logged in (not after a logout or a kick), it reconnects with the same token every 2 s for 30 s and the
 * server resends the room and match. Reloading the page reuses the token kept in sessionStorage.
 */
class GameStore {
  private state: State = initial;
  private readonly listeners = new Set<() => void>();
  private socket: GameSocket | null = null;
  private generation = 0; // events of an older socket are ignored
  private loggingOut = false;
  private kickReason: string | null = null;
  private reconnectUntil = 0;
  private noticeSeq = 0;
  private restoreStarted = false;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  getState = (): State => this.state;

  serverNow(): number {
    return this.socket ? this.socket.serverNow() : Date.now();
  }

  // ---------------------------------------------------------------- account

  async login(username: string, password: string): Promise<void> {
    const { token } = await rest.login(username, password);
    await this.startSession(token);
  }

  async register(username: string, password: string, displayName: string): Promise<void> {
    await rest.register(username, password, displayName.trim() || undefined);
    await this.login(username, password);
  }

  /** After a page reload: continue with the token of this tab, if any. */
  async restore(): Promise<void> {
    const token = sessionStorage.getItem(TOKEN_KEY);
    if (!token || this.restoreStarted) {
      return; // React StrictMode runs effects twice in dev
    }
    this.restoreStarted = true;
    try {
      await this.startSession(token);
    } catch {
      sessionStorage.removeItem(TOKEN_KEY);
      this.set({ connection: 'disconnected' });
    }
  }

  logout(): void {
    this.loggingOut = true;
    const socket = this.socket;
    if (!socket) {
      this.reset(null);
      return;
    }
    // the server leaves the room for us (forfeiting a running match) and closes the socket
    socket.request('LOGOUT').catch(() => undefined).finally(() => socket.close('Đã đăng xuất'));
  }

  // ---------------------------------------------------------------- lobby

  createRoom = (name: string) => this.roomRequest('CREATE_ROOM', name.trim() ? { name: name.trim() } : {});
  joinRoom = (roomId: string) => this.roomRequest('JOIN_ROOM', { roomId });
  rematch = () => this.roomRequest('REMATCH');

  async leaveRoom(): Promise<void> {
    await this.request('LEAVE_ROOM');
    this.set({ room: null, match: null });
  }

  async quickMatch(): Promise<void> {
    this.set({ queue: await this.request<QueueStatus>('QUICK_MATCH') });
  }

  async cancelQuickMatch(): Promise<void> {
    await this.request('CANCEL_QUICK_MATCH');
    this.set({ queue: { inQueue: false, since: null, ratingRange: null, queueSize: 0 } });
  }

  async invite(user: OnlineUser): Promise<void> {
    const { inviteId } = await this.request<{ inviteId: string }>('INVITE', { userId: user.id });
    this.set({ outgoing: { inviteId, name: user.displayName } });
  }

  async cancelInvite(): Promise<void> {
    const outgoing = this.state.outgoing;
    if (outgoing) {
      this.set({ outgoing: null });
      await this.request('CANCEL_INVITE', { inviteId: outgoing.inviteId });
    }
  }

  async answerInvite(inviteId: string, accept: boolean): Promise<void> {
    this.set({ invites: this.state.invites.filter((i) => i.inviteId !== inviteId) });
    if (accept) {
      await this.roomRequest('INVITE_REPLY', { inviteId, accept: true });
    } else {
      await this.request('INVITE_REPLY', { inviteId, accept: false });
    }
  }

  // ---------------------------------------------------------------- match

  /** The board only changes when MOVE_APPLIED arrives. */
  async move(x: number, y: number): Promise<void> {
    const match = this.state.match;
    if (match) {
      await this.request('MOVE', { matchId: match.matchId, x, y });
    }
  }

  async resign(): Promise<void> {
    const match = this.state.match;
    if (match) {
      await this.request('RESIGN', { matchId: match.matchId });
    }
  }

  // ---------------------------------------------------------------- notices

  notify(text: string, kind: Notice['kind'] = 'info'): void {
    const notice = { id: ++this.noticeSeq, text, kind };
    this.set({ notices: [...this.state.notices, notice] });
    setTimeout(() => this.dismiss(notice.id), NOTICE_MS);
  }

  dismiss(id: number): void {
    this.set({ notices: this.state.notices.filter((n) => n.id !== id) });
  }

  /** Runs an action and shows its error, if any, as a notice. */
  run(action: () => Promise<unknown>): void {
    action().catch((e) => this.notify(errorMessage(e), 'error'));
  }

  // ---------------------------------------------------------------- session and socket

  private async startSession(token: string): Promise<void> {
    this.set({ connection: 'connecting', logoutReason: null });
    const user = await this.connectAndAuth(token);
    sessionStorage.setItem(TOKEN_KEY, token);
    this.loggingOut = false;
    this.kickReason = null;
    this.set({ token, me: user, connection: 'connected' });
    this.synchronize();
  }

  /** Opens a socket and authenticates it. Rejects (and closes the socket) if either step fails. */
  private async connectAndAuth(token: string): Promise<UserInfo> {
    this.dropSocket();
    const generation = ++this.generation;
    const socket = await GameSocket.open(GameSocket.url(), {
      onEvent: (type, data) => {
        if (generation === this.generation) {
          this.handleEvent(type, data);
        }
      },
      onClose: (reason) => {
        if (generation === this.generation) {
          this.socketClosed(reason);
        }
      },
    });
    if (generation !== this.generation) {
      socket.close('Bỏ kết nối cũ'); // a newer connection was started meanwhile
      throw new ServerError('DISCONNECTED', 'Kết nối đã bị thay thế');
    }
    this.socket = socket;
    try {
      const { user } = await socket.request<{ user: UserInfo }>('AUTH', { token });
      return user;
    } catch (e) {
      this.dropSocket();
      throw e;
    }
  }

  private socketClosed(reason: string): void {
    this.socket = null;
    if (!this.state.me) {
      this.set({ connection: 'disconnected' });
      return;
    }
    if (this.loggingOut || this.kickReason) {
      this.reset(this.kickReason);
      return;
    }
    if (this.reconnectUntil === 0) {
      this.reconnectUntil = Date.now() + RECONNECT_WINDOW_MS;
      this.notify(`${reason}. Đang thử kết nối lại...`, 'error');
    }
    this.set({ connection: 'reconnecting' });
    setTimeout(() => this.reconnect(), RECONNECT_INTERVAL_MS);
  }

  private async reconnect(): Promise<void> {
    const token = this.state.token;
    if (!token || !this.state.me) {
      return;
    }
    try {
      const user = await this.connectAndAuth(token);
      this.reconnectUntil = 0;
      this.set({ me: user, connection: 'connected' });
      this.notify('Đã kết nối lại');
      this.synchronize();
    } catch (e) {
      const code = e instanceof ServerError ? e.code : '';
      if (code === 'UNAUTHORIZED' || code === 'ACCOUNT_LOCKED') {
        this.reset(errorMessage(e));
      } else if (Date.now() > this.reconnectUntil) {
        this.reset('Không thể kết nối lại tới server');
      } else {
        setTimeout(() => this.reconnect(), RECONNECT_INTERVAL_MS);
      }
    }
  }

  /** Room list, online list and my room/match/queue: after login and after every reconnect. */
  private synchronize(): void {
    this.request<{ rooms: RoomInfo[] }>('LIST_ROOMS').then((d) => this.set({ rooms: d.rooms })).catch(() => undefined);
    this.request<{ users: OnlineUser[] }>('LIST_ONLINE').then((d) => this.set({ online: d.users })).catch(() => undefined);
    this.request<{ room: RoomInfo | null; match: MatchState | null; queue: QueueStatus }>('GET_STATE')
      .then((d) => this.set({ room: d.room, match: d.match ? M.fromState(d.match) : null, queue: d.queue }))
      .catch(() => undefined);
  }

  private handleEvent(type: string, data: any): void {
    const match = this.state.match;
    const current = (matchId: string) => match !== null && match.matchId === matchId;
    switch (type) {
      case 'ROOM_LIST':
        this.set({ rooms: data.rooms });
        break;
      case 'ONLINE_USERS':
        this.set({ online: data.users });
        break;
      case 'ROOM_UPDATE':
        this.setRoom(data.room);
        break;
      case 'MATCH_START':
      case 'MATCH_STATE':
        this.set({ match: M.fromState(data as MatchState) });
        break;
      case 'MOVE_APPLIED':
        if (current((data as MoveApplied).matchId)) {
          this.set({ match: M.applyMove(match!, data) });
        }
        break;
      case 'MATCH_END':
        if (current((data as MatchResult).matchId)) {
          this.matchEnded(match!, data);
        }
        break;
      case 'OPPONENT_DISCONNECTED':
        if (current(data.matchId)) {
          this.set({ match: M.opponentDisconnected(match!, data.graceDeadline, this.serverNow()) });
          this.notify('Đối thủ mất kết nối. Trận tạm dừng trong lúc chờ đối thủ quay lại.');
        }
        break;
      case 'OPPONENT_RECONNECTED':
        if (current(data.matchId)) {
          this.set({ match: M.opponentReconnected(match!, data.turnDeadline) });
          this.notify('Đối thủ đã kết nối lại. Trận tiếp tục.');
        }
        break;
      case 'QUEUE_STATUS':
        this.set({ queue: data });
        break;
      case 'INVITED':
        this.set({ invites: [...this.state.invites, data as Invitation] });
        break;
      case 'INVITE_CLOSED':
        this.inviteClosed(data.inviteId, data.reason);
        break;
      case 'KICKED':
        this.kickReason = data.reason === 'LOGGED_IN_ELSEWHERE'
          ? 'Tài khoản của bạn vừa đăng nhập ở nơi khác'
          : 'Tài khoản của bạn đã bị khóa';
        break;
      case 'ERROR':
        this.notify(`Lỗi: ${data.message}`, 'error');
        break;
      default:
        break; // unknown event from a newer server
    }
  }

  private matchEnded(match: MatchView, result: MatchResult): void {
    const me = this.state.me;
    const change = result.ratingChange?.[match.myMark] ?? 0;
    this.set({
      match: M.finish(match, result),
      me: me && change !== 0 ? { ...me, rating: me.rating + change } : me,
    });
  }

  private inviteClosed(inviteId: string, reason: string): void {
    const outgoing = this.state.outgoing;
    this.set({ invites: this.state.invites.filter((i) => i.inviteId !== inviteId) });
    if (outgoing && outgoing.inviteId === inviteId) {
      this.set({ outgoing: null });
      const texts: Record<string, string> = {
        DECLINED: `${outgoing.name} đã từ chối lời mời.`,
        EXPIRED: `${outgoing.name} không trả lời, lời mời đã hết hạn.`,
        UNAVAILABLE: `${outgoing.name} không còn sẵn sàng.`,
      };
      if (texts[reason]) {
        this.notify(texts[reason]);
      }
    }
  }

  private setRoom(room: RoomInfo | null): void {
    const match = this.state.match;
    const otherRoom = room !== null && match !== null && match.roomId !== room.id;
    this.set({ room, match: otherRoom ? null : match });
  }

  private async roomRequest(type: string, data: object = {}): Promise<RoomInfo> {
    const { room } = await this.request<{ room: RoomInfo }>(type, data);
    this.setRoom(room);
    return room;
  }

  private request<T = any>(type: string, data: object = {}): Promise<T> {
    if (!this.socket) {
      return Promise.reject(new ServerError('DISCONNECTED', 'Chưa kết nối tới server'));
    }
    return this.socket.request<T>(type, data);
  }

  private dropSocket(): void {
    this.generation++;
    this.socket?.close('Bỏ kết nối');
    this.socket = null;
  }

  private reset(reason: string | null): void {
    this.dropSocket();
    sessionStorage.removeItem(TOKEN_KEY);
    this.loggingOut = false;
    this.kickReason = null;
    this.reconnectUntil = 0;
    this.set({ ...initial, logoutReason: reason, notices: this.state.notices });
  }

  private set(patch: Partial<State>): void {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((l) => l());
  }
}

export const store = new GameStore();

export function useStore(): State {
  return useSyncExternalStore(store.subscribe, store.getState);
}

/** The latest info of a player from my room (ratings change after a match). */
export function latestPlayer(room: RoomInfo | null, player: PlayerInfo): PlayerInfo {
  return room?.players.find((p) => p.id === player.id) ?? player;
}

/** For the debug console and tests: `window.caro.store`. */
declare global {
  interface Window {
    caro?: { store: GameStore };
  }
}
window.caro = { store };
