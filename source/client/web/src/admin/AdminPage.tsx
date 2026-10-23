import { useState } from 'react';

import type { AdminUserView, MatchSummary, Page, RoomInfo, SystemStats } from '../api/protocol';
import { rest } from '../api/rest';
import { useAction, useLoad } from '../components/hooks';
import { Pager, Stat, useModals } from '../components/Modals';
import { useStore } from '../state/store';
import * as T from '../texts';

type Tab = 'users' | 'rooms' | 'matches';

/** /api/admin/** (role ADMIN). Everything here goes through REST, not the game socket. */
export function AdminPage() {
  const { token } = useStore();
  const [tab, setTab] = useState<Tab>('users');
  const stats = useLoad<SystemStats>(() => rest.admin.stats(token!), []);
  return (
    <main className="admin">
      <section className="card">
        <header className="section-head">
          <h2>Thống kê hệ thống</h2>
          <button type="button" className="small" onClick={stats.reload}>Làm mới</button>
        </header>
        {stats.data && (
          <div className="stats">
            <Stat label="Tài khoản" value={stats.data.totalUsers} />
            <Stat label="Bị khóa" value={stats.data.lockedUsers} />
            <Stat label="Đang online" value={stats.data.onlineUsers} />
            <Stat label="Phòng mở" value={stats.data.rooms} />
            <Stat label="Trận đang đấu" value={stats.data.activeMatches} />
            <Stat label="Trận đã lưu" value={stats.data.totalMatches} />
            <Stat label="Trận 24 giờ qua" value={stats.data.matchesLast24h} />
          </div>
        )}
      </section>
      <div className="tabs">
        <button type="button" className={tab === 'users' ? 'active' : ''} onClick={() => setTab('users')}>Người dùng</button>
        <button type="button" className={tab === 'rooms' ? 'active' : ''} onClick={() => setTab('rooms')}>Phòng</button>
        <button type="button" className={tab === 'matches' ? 'active' : ''} onClick={() => setTab('matches')}>Trận đấu</button>
      </div>
      {tab === 'users' && <Users onChange={stats.reload} />}
      {tab === 'rooms' && <Rooms />}
      {tab === 'matches' && <Matches />}
    </main>
  );
}

function Users({ onChange }: { onChange: () => void }) {
  const { token } = useStore();
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [busy, run] = useAction();
  const users = useLoad<Page<AdminUserView>>(() => rest.admin.users(token!, search, page), [search, page]);

  const toggle = (u: AdminUserView) => {
    const locking = u.status === 'ACTIVE';
    if (locking && !confirm(`Khóa tài khoản ${u.username}? Nếu đang online, người này sẽ bị đá ra ngay.`)) return;
    run(async () => {
      await (locking ? rest.admin.lock(token!, u.id) : rest.admin.unlock(token!, u.id));
      users.reload();
      onChange();
    });
  };

  return (
    <section className="card">
      <form className="inline-form" onSubmit={(e) => { e.preventDefault(); setPage(0); setSearch(query.trim()); }}>
        <input placeholder="Tìm theo username hoặc tên hiển thị" value={query} onChange={(e) => setQuery(e.target.value)} />
        <button type="submit">Tìm</button>
      </form>
      {users.error && <p className="error">{users.error}</p>}
      {users.data && (
        <>
          <table>
            <thead>
              <tr><th className="num">ID</th><th>Username</th><th>Tên hiển thị</th><th>Vai trò</th><th className="num">Elo</th>
                <th>Trạng thái</th><th>Ngày tạo</th><th /></tr>
            </thead>
            <tbody>
              {users.data.items.map((u) => (
                <tr key={u.id}>
                  <td className="num">{u.id}</td>
                  <td>{u.username} {u.online && <span className="dot" title="Đang online" />}</td>
                  <td>{u.displayName}</td>
                  <td>{u.role === 'ADMIN' ? 'Admin' : 'Người chơi'}</td>
                  <td className="num">{u.rating}</td>
                  <td><span className={`badge account-${u.status}`}>{u.status === 'ACTIVE' ? 'Hoạt động' : 'Bị khóa'}</span></td>
                  <td>{T.date(u.createdAt)}</td>
                  <td className="right">
                    {u.role !== 'ADMIN' && (
                      <button type="button" className={`small ${u.status === 'ACTIVE' ? 'danger' : ''}`} disabled={busy}
                        onClick={() => toggle(u)}>
                        {u.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={users.data} onPage={setPage} />
        </>
      )}
    </section>
  );
}

function Rooms() {
  const { token } = useStore();
  const rooms = useLoad<RoomInfo[]>(() => rest.admin.rooms(token!), []);
  return (
    <section className="card">
      <header className="section-head">
        <h2>Phòng đang mở</h2>
        <button type="button" className="small" onClick={rooms.reload}>Làm mới</button>
      </header>
      {rooms.data && rooms.data.length === 0 && <p className="muted empty">Không có phòng nào.</p>}
      {rooms.data && rooms.data.length > 0 && (
        <table>
          <thead><tr><th>ID</th><th>Tên</th><th>Trạng thái</th><th>Người chơi</th></tr></thead>
          <tbody>
            {rooms.data.map((r) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                <td>{r.name}</td>
                <td><span className={`badge status-${r.status}`}>{T.roomStatus[r.status]}</span></td>
                <td>{r.players.map((p) => T.player(p)).join(' vs ')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function Matches() {
  const { token } = useStore();
  const modals = useModals();
  const [page, setPage] = useState(0);
  const matches = useLoad<Page<MatchSummary>>(() => rest.admin.matches(token!, page), [page]);
  return (
    <section className="card">
      {matches.data && (
        <>
          <table>
            <thead>
              <tr><th>Kết thúc</th><th>X</th><th>O</th><th>Kết quả</th><th>Lý do</th><th className="num">Elo X/O</th>
                <th className="num">Số nước</th><th /></tr>
            </thead>
            <tbody>
              {matches.data.items.map((m) => (
                <tr key={m.matchId}>
                  <td>{T.dateTime(m.endedAt)}</td>
                  <td>{T.player(m.playerX)}</td>
                  <td>{T.player(m.playerO)}</td>
                  <td>{T.result[m.result]}</td>
                  <td>{T.reason[m.reason]}</td>
                  <td className="num">{T.signed(m.ratingChange.X)} / {T.signed(m.ratingChange.O)}</td>
                  <td className="num">{m.totalMoves}</td>
                  <td className="right">
                    <button type="button" className="small" onClick={() => modals.openReplay(m.matchId)}>Xem lại</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={matches.data} onPage={setPage} />
        </>
      )}
    </section>
  );
}
