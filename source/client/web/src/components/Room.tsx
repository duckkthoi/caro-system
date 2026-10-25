import { useEffect, useRef, useState } from 'react';

import type { MatchResult, PlayerInfo, RoomInfo } from '../api/protocol';
import { BOT_ID } from '../api/protocol';
import type { MatchView } from '../state/match';
import { canPlay, isMyTurn, myRatingChange, opponentOf, turnTimeLeft } from '../state/match';
import { isSpectating, latestPlayer, store, useStore } from '../state/store';
import * as T from '../texts';
import { Board, Stone } from './Board';
import { useAction, useServerNow } from './hooks';

/** My room (or the room I watch): board, players, turn clock, match buttons and chat. */
export function Room() {
  const { room, match, me } = useStore();
  const [busy, run] = useAction();
  const [movePending, setMovePending] = useState(false);
  const [picking, setPicking] = useState(false);
  const live = match !== null && match.status !== 'FINISHED' && room?.status !== 'WAITING';
  const now = useServerNow(250, live);
  if (!room || !me) return null;
  const watching = isSpectating(room, me);

  const play = (x: number, y: number) => {
    if (!match || movePending || !canPlay(match, x, y)) return;
    setMovePending(true);
    store.move(x, y)
      .catch((e) => store.notify(e.message, 'error'))
      .finally(() => setMovePending(false));
  };
  const leave = () => {
    if (live && !watching && !confirm('Rời phòng khi đang đấu sẽ bị tính là thua. Tiếp tục?')) return;
    run(() => store.leaveRoom());
  };
  const resign = () => {
    if (confirm('Bạn chắc chắn muốn đầu hàng?')) run(() => store.resign());
  };

  const [x, o] = seats(room, match);
  const turn = live && match?.status === 'IN_PROGRESS' ? match.turn : null;
  const timeLeft = match ? turnTimeLeft(match, now) : 0;
  const askedRematch = room.rematchRequests.includes(me.id);
  const empty = Array.from({ length: 15 }, () => '.'.repeat(15));

  return (
    <main className="room">
      <Board
        board={match?.board ?? empty}
        lastMove={match?.lastMove}
        winLine={match?.result?.winLine}
        playable={match && isMyTurn(match) && !movePending ? match.myMark : null}
        onPlay={play}
      />
      <aside className="side card">
        <h2>{room.name}</h2>
        <RoomTags room={room} watching={watching} />
        <PlayerCard mark="X" player={x} me={me.id} active={turn === 'X'} />
        <PlayerCard mark="O" player={o} me={me.id} active={turn === 'O'} />

        {live && match && (
          <div className={`clock ${timeLeft <= 10_000 && match.status === 'IN_PROGRESS' ? 'low' : ''}`}>
            {T.clock(timeLeft)}
          </div>
        )}
        <p className="status">
          {watching ? spectatorText(room, match, now) : statusText(room, match, me.id, now)}
        </p>

        {watching ? (
          <div className="side-actions">
            <button type="button" disabled={busy} onClick={leave}>Thôi xem</button>
          </div>
        ) : (
          <div className="side-actions">
            {room.status === 'WAITING' && (
              <button type="button" className="primary" onClick={() => setPicking(true)}>Mời người chơi</button>
            )}
            {room.status === 'FINISHED' && (
              <button type="button" className="primary" disabled={busy || askedRematch} onClick={() => run(() => store.rematch())}>
                {askedRematch ? 'Đã gửi yêu cầu chơi lại' : 'Chơi lại (đổi quân)'}
              </button>
            )}
            {live && <button type="button" disabled={busy} onClick={resign}>Đầu hàng</button>}
            <button type="button" disabled={busy} onClick={leave}>Rời phòng</button>
          </div>
        )}
        {room.spectators.length > 0 && (
          <p className="muted spectators">
            Người xem ({room.spectators.length}): {room.spectators.map((p) => p.displayName).join(', ')}
          </p>
        )}
        <Chat />
      </aside>
      {picking && <InvitePicker onClose={() => setPicking(false)} />}
    </main>
  );
}

/** Players in their seats: the match marks while there is a match, otherwise host first. */
function seats(room: RoomInfo, match: MatchView | null): [PlayerInfo | null, PlayerInfo | null] {
  if (match && room.status !== 'WAITING') {
    return [latestPlayer(room, match.playerX), latestPlayer(room, match.playerO)];
  }
  return [room.players[0] ?? null, room.players[1] ?? null];
}

function RoomTags({ room, watching }: { room: RoomInfo; watching: boolean }) {
  if (!watching && !room.blockTwoEnds && !room.botLevel) return null;
  return (
    <div className="tags">
      {watching && <span className="badge presence-SPECTATING">Bạn đang xem</span>}
      {room.botLevel && <span className="badge">Chơi với máy · {T.botLevel[room.botLevel]}</span>}
      {room.blockTwoEnds && <span className="badge">{T.BLOCK_TWO_ENDS}</span>}
    </div>
  );
}

function spectatorText(room: RoomInfo, match: MatchView | null, now: number): string {
  if (room.status === 'WAITING' || !match) return 'Phòng đang chờ người chơi thứ hai.';
  if (match.status === 'FINISHED') {
    const r = match.result;
    return r ? `${T.result[r.result]}: ${T.reason[r.reason]}.` : 'Trận đấu đã kết thúc.';
  }
  if (match.status === 'PAUSED') {
    return `Một người chơi mất kết nối. Chờ quay lại: ${T.clock((match.graceDeadline ?? now) - now)}`;
  }
  const player = match.turn === 'X' ? match.playerX : match.playerO;
  return `Đến lượt ${player.displayName} (${match.turn})`;
}

function statusText(room: RoomInfo, match: MatchView | null, myId: number, now: number): string {
  if (room.status === 'WAITING') return 'Đang chờ đối thủ vào phòng... Bạn có thể mời một người đang online.';
  if (!match) return '';
  if (match.status === 'FINISHED') {
    let text = resultText(match, myId);
    const opponentAsked = room.rematchRequests.includes(opponentOf(match).id);
    if (room.rematchRequests.includes(myId)) text += ' Đang chờ đối thủ đồng ý chơi lại...';
    else if (opponentAsked) text += ' Đối thủ muốn chơi lại!';
    return text;
  }
  if (match.status === 'PAUSED') {
    return `Đối thủ mất kết nối. Chờ đối thủ quay lại: ${T.clock((match.graceDeadline ?? now) - now)}`;
  }
  return isMyTurn(match) ? 'Đến lượt bạn' : 'Đang chờ đối thủ đi...';
}

function resultText(match: MatchView, myId: number): string {
  const r: MatchResult | null = match.result;
  if (!r) return 'Trận đấu đã kết thúc.';
  const elo = match.rated ? ` (${T.signed(myRatingChange(match, r))} Elo)` : '';
  if (r.result === 'ABORTED') return `Trận bị hủy: ${T.reason[r.reason]}. Không tính điểm.`;
  if (r.result === 'DRAW') return `Hòa: ${T.reason[r.reason]}${elo}.`;
  return `${r.winnerId === myId ? 'Bạn thắng!' : 'Bạn thua.'} ${T.reason[r.reason]}${elo}.`;
}

function PlayerCard({ mark, player, me, active }: { mark: 'X' | 'O'; player: PlayerInfo | null; me: number; active: boolean }) {
  return (
    <div className={`player-card ${active ? `active active-${mark}` : ''}`}>
      <Stone mark={mark} />
      <div>
        <strong>{player ? player.displayName : '(trống)'}{player?.id === me && <span className="you"> (bạn)</span>}</strong>
        <span className="muted">
          {!player ? 'Đang chờ người chơi' : player.id === BOT_ID ? 'Máy tính' : `@${player.username} · Elo ${player.rating}`}
        </span>
      </div>
    </div>
  );
}

function InvitePicker({ onClose }: { onClose: () => void }) {
  const { online, me, outgoing } = useStore();
  const [busy, run] = useAction();
  const candidates = online.filter((u) => u.id !== me?.id && T.invitable(u.status));
  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-label="Mời người chơi">
        <header>
          <h2>Mời người chơi</h2>
          <button type="button" className="ghost" onClick={onClose} aria-label="Đóng">✕</button>
        </header>
        <div className="modal-body">
          {candidates.length === 0 ? (
            <p className="muted">Hiện không có người chơi nào rảnh để mời.</p>
          ) : (
            <ul className="people">
              {candidates.map((u) => (
                <li key={u.id}>
                  <strong>{u.displayName}</strong>
                  <span className="muted"> @{u.username} · {u.rating}</span>
                  <span className={`badge presence-${u.status}`}>{T.presence[u.status]}</span>
                  <button
                    type="button"
                    className="small primary"
                    disabled={busy || outgoing !== null}
                    onClick={() => run(async () => { await store.invite(u); onClose(); })}
                  >
                    Mời
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

/** Room chat (docs/rules.md, section 11). Messages are plain text: React escapes them. */
function Chat() {
  const { chat, me } = useStore();
  const [text, setText] = useState('');
  const [busy, run] = useAction();
  const log = useRef<HTMLDivElement>(null);
  useEffect(() => {
    log.current?.scrollTo({ top: log.current.scrollHeight });
  }, [chat.length]);
  const send = () => {
    const message = text.trim();
    if (!message) return;
    run(async () => {
      await store.sendChat(message);
      setText('');
    });
  };
  return (
    <section className="chat">
      <h3>Trò chuyện</h3>
      <div className="chat-log" ref={log} aria-live="polite">
        {chat.length === 0 && <p className="muted">Chưa có tin nhắn.</p>}
        {chat.map((m) => (
          <p key={m.id} className={m.from.id === me?.id ? 'mine' : ''}>
            <strong>{m.from.displayName}</strong>
            {m.spectator && <span className="muted"> (người xem)</span>}
            <span className="muted"> · {T.time(m.sentAt)}</span>
            <br />
            {m.text}
          </p>
        ))}
      </div>
      <form className="chat-form" onSubmit={(e) => { e.preventDefault(); send(); }}>
        <input value={text} maxLength={200} placeholder="Nhập tin nhắn..." onChange={(e) => setText(e.target.value)} />
        <button type="submit" disabled={busy || !text.trim()}>Gửi</button>
      </form>
    </section>
  );
}
