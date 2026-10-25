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
          <p className="muted empty">Chưa có phòng nào. Hãy tạo phòng hoặc tìm trận nhanh.</p>
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
    <section className="card quick">
      <div>
        <h2>Chơi với máy</h2>
        <p className="muted">Luyện tập với máy. Không tính Elo và không lưu vào lịch sử.</p>
      </div>
      <div className="inline-form bot-form">
        <select value={level} onChange={(e) => setLevel(e.target.value as BotLevel)} aria-label="Mức độ">
          <option value="EASY">Dễ</option>
          <option value="HARD">Khó</option>
        </select>
        <label className="check">
          <input type="checkbox" checked={blockTwoEnds} onChange={(e) => setBlockTwoEnds(e.target.checked)} />
          Chặn hai đầu
        </label>
        <button type="button" className="primary" disabled={busy} onClick={() => run(() => store.playBot(level, blockTwoEnds))}>
          Chơi
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
