import { useState } from 'react';

import type { BotLevel, OnlineUser, RoomInfo } from '../api/protocol';
import { store, useStore } from '../state/store';
import * as T from '../texts';
import { useAction, useServerNow } from './hooks';
import { useModals } from './Modals';

/** Rooms, online players, quick match and playing against the computer. */
export function Lobby() {
  const { rooms, online, me, queue } = useStore();
  const modals = useModals();
  const [busy, run] = useAction();
  const [roomName, setRoomName] = useState('');
  const [blockTwoEnds, setBlockTwoEnds] = useState(false);
  const inQueue = queue?.inQueue ?? false;

  return (
    <main className="lobby">
      <QuickMatch />
      <PlayBot />

      <section className="card">
        <header className="section-head">
          <h2>Phòng đang mở</h2>
          <form
            className="inline-form"
            onSubmit={(e) => {
              e.preventDefault();
              run(() => store.createRoom(roomName, blockTwoEnds));
              setRoomName('');
            }}
          >
            <input placeholder="Tên phòng (không bắt buộc)" value={roomName} maxLength={40}
              onChange={(e) => setRoomName(e.target.value)} />
            <label className="check" title="5 quân bị chặn cả hai đầu không tính thắng">
              <input type="checkbox" checked={blockTwoEnds} onChange={(e) => setBlockTwoEnds(e.target.checked)} />
              Chặn hai đầu
            </label>
            <button type="submit" className="primary" disabled={busy}>Tạo phòng</button>
          </form>
        </header>
        {rooms.length === 0 ? (
          <div className="empty" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.5rem' }}>
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--text-dim)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <rect x="2" y="3" width="20" height="14" rx="2" />
              <line x1="8" y1="21" x2="16" y2="21" />
              <line x1="12" y1="17" x2="12" y2="21" />
            </svg>
            <p style={{ margin: 0, fontWeight: 500, color: 'var(--text)' }}>Chưa có phòng nào đang mở</p>
            <p className="muted" style={{ margin: 0, fontSize: '0.85rem' }}>Hãy nhập tên ở trên để tạo phòng mới hoặc bấm "Tìm trận" để ghép đôi tự động.</p>
          </div>
        ) : (
          <table>
            <thead>
              <tr><th>Phòng</th><th>Chủ phòng</th><th>Trạng thái</th><th className="num">Người chơi</th><th className="num">Xem</th><th /></tr>
            </thead>
            <tbody>
              {rooms.map((r) => (
                <tr key={r.id}>
                  <td>{r.name}<RoomBadges room={r} /></td>
                  <td>{T.player(r.players[0])}</td>
                  <td><span className={`badge status-${r.status}`}>{T.roomStatus[r.status]}</span></td>
                  <td className="num">{r.players.length}/2</td>
                  <td className="num">{r.spectators.length}</td>
                  <td className="right actions-cell">
                    {r.status === 'WAITING' && (
                      <button type="button" className="small" disabled={busy} onClick={() => run(() => store.joinRoom(r.id))}>
                        Vào phòng
                      </button>
                    )}
                    <button type="button" className="small" disabled={busy} onClick={() => run(() => store.spectate(r.id))}>
                      Xem
                    </button>
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

function RoomBadges({ room }: { room: RoomInfo }) {
  return (
    <>
      {room.botLevel && <span className="badge tag">Máy · {T.botLevel[room.botLevel]}</span>}
      {room.blockTwoEnds && <span className="badge tag">Chặn hai đầu</span>}
    </>
  );
}

function InviteButton({ user, disabled }: { user: OnlineUser; disabled: boolean }) {
  const { outgoing } = useStore();
  const [busy, run] = useAction();
  const invitable = T.invitable(user.status);
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

/** Play against the computer (docs/rules.md, section 12): unrated, not saved in the history. */
function PlayBot() {
  const [busy, run] = useAction();
  const [level, setLevel] = useState<BotLevel>('EASY');
  const [blockTwoEnds, setBlockTwoEnds] = useState(false);
  return (
    <section className="card quick" style={{ borderLeftColor: '#6366f1' }}>
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <h2>Chơi với máy</h2>
          <span className="badge" style={{ background: '#e0e7ff', color: '#4338ca', borderColor: '#c7d2fe' }}>Luyện tập</span>
        </div>
        <p className="muted">Luyện tập tư duy cùng AI thông minh. Trận đấu không tính điểm Elo và không lưu vào lịch sử.</p>
      </div>
      <div className="inline-form bot-form">
        <select value={level} onChange={(e) => setLevel(e.target.value as BotLevel)} aria-label="Mức độ">
          <option value="EASY">Cấp độ: Dễ</option>
          <option value="HARD">Cấp độ: Khó</option>
        </select>
        <label className="check">
          <input type="checkbox" checked={blockTwoEnds} onChange={(e) => setBlockTwoEnds(e.target.checked)} />
          Chặn 2 đầu
        </label>
        <button type="button" className="primary" disabled={busy} onClick={() => run(() => store.playBot(level, blockTwoEnds))}>
          Bắt đầu chơi
        </button>
      </div>
    </section>
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
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <h2>Tìm trận nhanh (Ranked)</h2>
          {inQueue && <span className="badge presence-IN_QUEUE">Đang ghép cặp...</span>}
        </div>
        {inQueue && queue ? (
          <p style={{ color: 'var(--text)', marginTop: '0.25rem' }}>
            Đang tìm đối thủ ({queue.ratingRange === null ? 'bất kỳ ai' : `chênh lệch Elo ±${queue.ratingRange}`})
            {' · '}Đã chờ: <strong>{T.clock(now - (queue.since ?? now))}</strong>{' · '}{queue.queueSize} người trong hàng chờ
          </p>
        ) : (
          <p className="muted">Ghép đối thủ ngẫu nhiên có xếp hạng Elo tương đương. Đấu rank tính điểm xếp hạng.</p>
        )}
      </div>
      <button
        type="button"
        className={inQueue ? 'danger' : 'primary'}
        disabled={busy}
        style={{ padding: '0.625rem 1.25rem', fontSize: '0.925rem' }}
        onClick={() => run(() => (inQueue ? store.cancelQuickMatch() : store.quickMatch()))}
      >
        {inQueue ? 'Hủy tìm trận' : 'Tìm trận ngay'}
      </button>
    </section>
  );
}
