import { useState } from 'react';

import type { OnlineUser } from '../api/protocol';
import { store, useStore } from '../state/store';
import * as T from '../texts';
import { useAction, useServerNow } from './hooks';
import { useModals } from './Modals';

/** Rooms, online players and quick match. */
export function Lobby() {
  const { rooms, online, me, queue } = useStore();
  const modals = useModals();
  const [busy, run] = useAction();
  const [roomName, setRoomName] = useState('');
  const inQueue = queue?.inQueue ?? false;

  return (
    <main className="lobby">
      <QuickMatch />

      <section className="card">
        <header className="section-head">
          <h2>Phòng đang mở</h2>
          <form
            className="inline-form"
            onSubmit={(e) => {
              e.preventDefault();
              run(() => store.createRoom(roomName));
              setRoomName('');
            }}
          >
            <input placeholder="Tên phòng (không bắt buộc)" value={roomName} maxLength={40}
              onChange={(e) => setRoomName(e.target.value)} />
            <button type="submit" className="primary" disabled={busy}>Tạo phòng</button>
          </form>
        </header>
        {rooms.length === 0 ? (
          <p className="muted empty">Chưa có phòng nào. Hãy tạo phòng hoặc tìm trận nhanh.</p>
        ) : (
          <table>
            <thead><tr><th>Phòng</th><th>Chủ phòng</th><th>Trạng thái</th><th className="num">Người chơi</th><th /></tr></thead>
            <tbody>
              {rooms.map((r) => (
                <tr key={r.id}>
                  <td>{r.name}</td>
                  <td>{T.player(r.players[0])}</td>
                  <td><span className={`badge status-${r.status}`}>{T.roomStatus[r.status]}</span></td>
                  <td className="num">{r.players.length}/2</td>
                  <td className="right">
                    {r.status === 'WAITING' && (
                      <button type="button" className="small" disabled={busy} onClick={() => run(() => store.joinRoom(r.id))}>
                        Vào phòng
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card">
        <header className="section-head"><h2>Người chơi online <span className="count">{online.length}</span></h2></header>
        <ul className="people">
          {online.map((u) => (
            <li key={u.id}>
              <button type="button" className="link" onClick={() => modals.openProfile(u.id)}>{u.displayName}</button>
              <span className="muted"> · {u.rating}</span>
              <span className={`badge presence-${u.status}`}>{T.presence[u.status]}</span>
              {u.id !== me?.id && <InviteButton user={u} disabled={inQueue} />}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}

function InviteButton({ user, disabled }: { user: OnlineUser; disabled: boolean }) {
  const { outgoing } = useStore();
  const [busy, run] = useAction();
  const invitable = user.status === 'IDLE' || user.status === 'IN_QUEUE';
  return (
    <button
      type="button"
      className="small"
      disabled={!invitable || disabled || busy || outgoing !== null}
      onClick={() => run(() => store.invite(user))}
    >
      Mời
    </button>
  );
}

function QuickMatch() {
  const { queue } = useStore();
  const [busy, run] = useAction();
  const inQueue = queue?.inQueue ?? false;
  const now = useServerNow(1000, inQueue);
  return (
    <section className={`card quick ${inQueue ? 'searching' : ''}`}>
      <div>
        <h2>Tìm trận nhanh</h2>
        {inQueue && queue ? (
          <p>
            Đang tìm đối thủ ({queue.ratingRange === null ? 'bất kỳ ai' : `chênh lệch Elo ±${queue.ratingRange}`})
            {' · '}đã chờ {T.clock(now - (queue.since ?? now))}{' · '}{queue.queueSize} người trong hàng chờ
          </p>
        ) : (
          <p className="muted">Ghép ngẫu nhiên với người có Elo gần bạn. Chờ càng lâu, khoảng điểm càng rộng.</p>
        )}
      </div>
      <button
        type="button"
        className={inQueue ? '' : 'primary'}
        disabled={busy}
        onClick={() => run(() => (inQueue ? store.cancelQuickMatch() : store.quickMatch()))}
      >
        {inQueue ? 'Hủy tìm trận' : 'Tìm trận'}
      </button>
    </section>
  );
}
