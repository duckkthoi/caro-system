import { useEffect, useRef, useState } from 'react';

import type { MatchResult, PlayerInfo, RoomInfo } from '../api/protocol';
import { BOT_ID } from '../api/protocol';
import type { MatchView } from '../state/match';
import { canPlay, isMyTurn, myRatingChange, opponentOf, turnTimeLeft } from '../state/match';
import { isSpectating, latestPlayer, store, useStore } from '../state/store';
import * as T from '../texts';
import { Board, Stone } from './Board';
import { useConfirm } from './Confirm';
import { useAction, useServerNow } from './hooks';

/** My room (or the room I watch): board, players, turn clock, match buttons and chat. */
export function Room() {
  const { room, match, me } = useStore();
  const [busy, run] = useAction();
  const confirm = useConfirm();
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
  const leave = async () => {
    if (live && !watching && !(await confirm({
      title: 'Rời phòng thi đấu?',
      message: 'Trận đấu đang diễn ra. Nếu rời phòng lúc này, bạn sẽ bị xử thua ngay lập tức.',
      confirmLabel: 'Rời phòng',
      cancelLabel: 'Ở lại chơi',
      danger: true,
    }))) return;
    run(() => store.leaveRoom());
  };
  const resign = async () => {
    if (await confirm({
      title: 'Xác nhận đầu hàng?',
      message: 'Bạn sẽ nhận kết quả thua ván này ngay lập tức.',
      confirmLabel: 'Đầu hàng',
      cancelLabel: 'Tiếp tục đấu',
      danger: true,
    })) run(() => store.resign());
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
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem' }}>
          <h2>{room.name}</h2>
          <RoomTags room={room} watching={watching} />
        </div>

        {/* VS Battle Arena Players */}
        <div className="vs-arena">
          <PlayerCard mark="X" player={x} me={me.id} active={turn === 'X'} />
          <div style={{ textAlign: 'center', fontSize: '0.75rem', fontWeight: 700, color: '#94a3b8', letterSpacing: '0.1em' }}>
            VS
          </div>
          <PlayerCard mark="O" player={o} me={me.id} active={turn === 'O'} />
        </div>

        {/* Precision Clock Widget */}
        {live && match && (
          <div className="clock-widget">
            <span className="clock-widget-label">Thời gian lượt đi</span>
            <div className={`clock ${timeLeft <= 10_000 && match.status === 'IN_PROGRESS' ? 'low' : ''}`}>
              {T.clock(timeLeft)}
            </div>
          </div>
        )}

        <div className="status-box">
          <p className="status">
            {watching ? spectatorText(room, match, now) : statusText(room, match, me.id, now)}
          </p>
        </div>

        {watching ? (
          <div className="side-actions">
            <button type="button" disabled={busy} onClick={leave}>Thôi xem</button>
          </div>
        ) : (
          <div className="side-actions">
            {room.status === 'WAITING' && (
              <button type="button" className="primary" onClick={() => setPicking(true)}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                  <circle cx="8.5" cy="7" r="4" />
                  <line x1="20" y1="8" x2="20" y2="14" />
                  <line x1="23" y1="11" x2="17" y2="11" />
                </svg>
                Mời người chơi
              </button>
            )}
            {room.status === 'FINISHED' && (
              <button type="button" className="primary" disabled={busy || askedRematch} onClick={() => run(() => store.rematch())}>
                {askedRematch ? 'Đã gửi lời mời tái đấu...' : 'Tái đấu (Đổi quân)'}
              </button>
            )}
            {live && match && <DrawControls match={match} meId={me.id} />}
            {live && <button type="button" className="danger" disabled={busy} onClick={resign}>Đầu hàng</button>}
            <button type="button" disabled={busy} onClick={leave}>Rời phòng</button>
          </div>
        )}

        {watching && live && match?.drawOfferFrom != null && (
          <p className="draw-note">{nameOf(match, match.drawOfferFrom)} đề nghị hòa ván này.</p>
        )}

        {room.spectators.length > 0 && (
          <p className="muted spectators">
            Khán giả ({room.spectators.length}): {room.spectators.map((p) => p.displayName).join(', ')}
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

/** Offer a draw, or answer the opponent's offer (docs/rules.md, section 14). */
function DrawControls({ match, meId }: { match: MatchView; meId: number }) {
  const [busy, run] = useAction();
  const offer = match.drawOfferFrom;
  if (offer !== null && offer !== meId) {
    return (
      <div className="draw-offer" role="alert">
        <p><strong>Đối thủ gửi đề nghị hòa.</strong> Nếu đồng ý, ván cờ sẽ kết thúc với kết quả hòa.</p>
        <div className="actions">
          <button type="button" disabled={busy} onClick={() => run(() => store.answerDraw(false))}>Từ chối</button>
          <button type="button" className="primary" disabled={busy} onClick={() => run(() => store.answerDraw(true))}>
            Đồng ý hòa
          </button>
        </div>
      </div>
    );
  }
  return (
    <button type="button" disabled={busy || offer !== null || match.status !== 'IN_PROGRESS'}
      onClick={() => run(() => store.offerDraw())}>
      {offer === meId ? 'Đã gửi lời cầu hòa...' : 'Cầu hòa'}
    </button>
  );
}

function nameOf(match: MatchView, userId: number): string {
  return match.playerX.id === userId ? match.playerX.displayName : match.playerO.displayName;
}

function RoomTags({ room, watching }: { room: RoomInfo; watching: boolean }) {
  if (!watching && !room.blockTwoEnds && !room.botLevel) return null;
  return (
    <div className="tags">
      {watching && <span className="badge presence-SPECTATING">Đang theo dõi</span>}
      {room.botLevel && <span className="badge tag" style={{ background: '#f5f3ff', color: '#6d28d9', borderColor: '#ddd6fe' }}>Máy · {T.botLevel[room.botLevel]}</span>}
      {room.blockTwoEnds && <span className="badge tag" style={{ background: '#fef3c7', color: '#92400e', borderColor: '#fde68a' }}>{T.BLOCK_TWO_ENDS}</span>}
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
    return `Người chơi tạm mất kết nối. Chờ quay lại: ${T.clock((match.graceDeadline ?? now) - now)}`;
  }
  const player = match.turn === 'X' ? match.playerX : match.playerO;
  return `Đang đến lượt ${player.displayName} (${match.turn})`;
}

function statusText(room: RoomInfo, match: MatchView | null, myId: number, now: number): string {
  if (room.status === 'WAITING') return 'Đang chờ đối thủ vào phòng... Bạn có thể mời một người chơi đang online.';
  if (!match) return '';
  if (match.status === 'FINISHED') {
    let text = resultText(match, myId);
    const opponentAsked = room.rematchRequests.includes(opponentOf(match).id);
    if (room.rematchRequests.includes(myId)) text += ' Đang chờ đối thủ đồng ý tái đấu...';
    else if (opponentAsked) text += ' Đối thủ đề nghị tái đấu!';
    return text;
  }
  if (match.status === 'PAUSED') {
    return `Đối thủ mất kết nối. Đang chờ quay lại: ${T.clock((match.graceDeadline ?? now) - now)}`;
  }
  return isMyTurn(match) ? '🎯 Đến lượt bạn đi quân!' : '⏳ Đang chờ đối thủ suy nghĩ...';
}

function resultText(match: MatchView, myId: number): string {
  const r: MatchResult | null = match.result;
  if (!r) return 'Trận đấu đã kết thúc.';
  const elo = match.rated ? ` (${T.signed(myRatingChange(match, r))} Elo)` : '';
  if (r.result === 'ABORTED') return `Trận đấu bị hủy: ${T.reason[r.reason]}. Không tính điểm.`;
  if (r.result === 'DRAW') return `Ván cờ kết thúc Hòa: ${T.reason[r.reason]}${elo}.`;
  return `${r.winnerId === myId ? '🏆 Bạn đã chiến thắng!' : '😢 Bạn đã thua.'} ${T.reason[r.reason]}${elo}.`;
}

function PlayerCard({ mark, player, me, active }: { mark: 'X' | 'O'; player: PlayerInfo | null; me: number; active: boolean }) {
  return (
    <div className={`player-card ${active ? `active active-${mark}` : ''}`}>
      <Stone mark={mark} />
      <div>
        <strong>
          {player ? player.displayName : '(Đang chờ...)'}
          {player?.id === me && <span className="you"> (Bạn)</span>}
        </strong>
        <span className="muted">
          {!player ? 'Chưa có người vào' : player.id === BOT_ID ? 'Trí tuệ nhân tạo' : `@${player.username} · Elo ${player.rating}`}
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
          <h2>Mời đối thủ thi đấu</h2>
          <button type="button" className="ghost" onClick={onClose} aria-label="Đóng">✕</button>
        </header>
        <div className="modal-body">
          {candidates.length === 0 ? (
            <div className="empty">
              <p className="muted">Hiện tại không có người chơi nào đang rảnh để mời.</p>
            </div>
          ) : (
            <ul className="people">
              {candidates.map((u) => (
                <li key={u.id}>
                  <span className="avatar-badge">{u.displayName.charAt(0).toUpperCase()}</span>
                  <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
                    <strong>{u.displayName}</strong>
                    <span className="muted" style={{ fontSize: '0.8rem' }}>@{u.username} · Elo {u.rating}</span>
                  </div>
                  <span className={`badge presence-${u.status}`}>{T.presence[u.status]}</span>
                  <button
                    type="button"
                    className="small primary"
                    disabled={busy || outgoing !== null}
                    onClick={() => run(async () => { await store.invite(u); onClose(); })}
                  >
                    Mời đấu
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

/** Room chat. */
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
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <h3>Trò chuyện trận đấu</h3>
        <span className="muted" style={{ fontSize: '0.75rem' }}>{chat.length} tin nhắn</span>
      </div>
      <div className="chat-log" ref={log} aria-live="polite">
        {chat.length === 0 && <p className="muted" style={{ textAlign: 'center', margin: 'auto 0' }}>Chưa có tin nhắn nào trong phòng.</p>}
        {chat.map((m) => (
          <p key={m.id} className={m.from.id === me?.id ? 'mine' : ''}>
            <strong>{m.from.displayName}</strong>
            {m.spectator && <span className="muted"> (Khán giả)</span>}
            <span className="muted" style={{ fontSize: '0.75rem' }}> · {T.time(m.sentAt)}</span>
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
