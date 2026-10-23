import { ServerError } from './protocol';

export interface SocketHandlers {
  /** A pushed event such as MOVE_APPLIED. */
  onEvent(type: string, data: any): void;
  /** Called once, whatever the reason the socket closed. */
  onClose(reason: string): void;
}

interface Pending {
  resolve(data: any): void;
  reject(error: ServerError): void;
  timer: ReturnType<typeof setTimeout>;
}

const REQUEST_TIMEOUT_MS = 10_000;
const PING_INTERVAL_MS = 10_000;

/**
 * The game WebSocket: one JSON message per frame, envelope {"type","reqId","data"} (docs/protocol.md).
 * request() returns a promise settled by the OK/ERROR carrying the same reqId; messages without a reqId are
 * events. Sends a PING every 10 s (the server closes silent connections after 30 s) and keeps the offset
 * between the server clock and this machine's clock, for countdowns.
 */
export class GameSocket {
  private readonly pending = new Map<string, Pending>();
  private seq = 0;
  private clockOffset = 0;
  private pingTimer: ReturnType<typeof setInterval> | undefined;
  private closed = false;

  private constructor(private readonly ws: WebSocket, private readonly handlers: SocketHandlers) {
    ws.onmessage = (e) => this.dispatch(JSON.parse(e.data as string));
    ws.onclose = () => this.finish('Mất kết nối tới server');
    this.ping();
    this.pingTimer = setInterval(() => this.ping(), PING_INTERVAL_MS);
  }

  /** Same host as the page: in dev Vite proxies /ws, in Docker nginx does. */
  static url(): string {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/ws`;
  }

  static open(url: string, handlers: SocketHandlers): Promise<GameSocket> {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(url);
      ws.onopen = () => resolve(new GameSocket(ws, handlers));
      ws.onerror = () => reject(new ServerError('DISCONNECTED', 'Không kết nối được tới server'));
    });
  }

  request<T = any>(type: string, data: object = {}): Promise<T> {
    if (this.closed) {
      return Promise.reject(new ServerError('DISCONNECTED', 'Mất kết nối tới server'));
    }
    const reqId = `w${++this.seq}`;
    return new Promise<T>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(reqId);
        reject(new ServerError('TIMEOUT', 'Server không phản hồi'));
      }, REQUEST_TIMEOUT_MS);
      this.pending.set(reqId, { resolve, reject, timer });
      this.ws.send(JSON.stringify({ type, reqId, data }));
    });
  }

  /** Current time on the server clock, in epoch ms. */
  serverNow(): number {
    return Date.now() + this.clockOffset;
  }

  close(reason = 'Đã đóng kết nối'): void {
    this.ws.close();
    this.finish(reason);
  }

  private dispatch(message: { type: string; reqId?: string; data?: any }): void {
    const pending = message.reqId ? this.pending.get(message.reqId) : undefined;
    if (!pending) {
      this.handlers.onEvent(message.type, message.data ?? {});
      return;
    }
    this.pending.delete(message.reqId!);
    clearTimeout(pending.timer);
    if (message.type === 'ERROR') {
      pending.reject(new ServerError(message.data?.code ?? 'ERROR', message.data?.message ?? 'Lỗi'));
    } else {
      pending.resolve(message.data ?? {});
    }
  }

  private ping(): void {
    const sentAt = Date.now();
    this.request<{ serverTime: number }>('PING')
      .then((pong) => {
        // the server read its clock roughly half way through the round trip
        this.clockOffset = pong.serverTime - (sentAt + Date.now()) / 2;
      })
      .catch(() => undefined);
  }

  private finish(reason: string): void {
    if (this.closed) {
      return;
    }
    this.closed = true;
    clearInterval(this.pingTimer);
    for (const p of this.pending.values()) {
      clearTimeout(p.timer);
      p.reject(new ServerError('DISCONNECTED', 'Mất kết nối tới server'));
    }
    this.pending.clear();
    this.handlers.onClose(reason);
  }
}
