import { useState } from 'react';

import type { BotLevel, OnlineUser, RoomInfo } from '../api/protocol';
import { store, useStore } from '../state/store';
import * as T from '../texts';
import { useAction, useServerNow } from './hooks';
import { useModals } from './Modals';

/** Unified Gaming Lobby: Hero player overview, side-by-side game modes, and structured arena. */
export function Lobby() {
  const { rooms, online, me, queue } = useStore();
  const modals = useModals();
  const [busy, run] = useAction();
  const [roomName, setRoomName] = useState('');
  const [blockTwoEnds, setBlockTwoEnds] = useState(false);
  const inQueue = queue?.inQueue ?? false;

  return (
    <main className="lobby-unified">
      {/* 1. Compact Player Overview */}
      {me && <HeroBanner user={me} />}

      {/* 2. Unified Game Modes Section (Ranked & AI Side-by-Side) */}
      <section className="modes-section">
        <div className="section-title-wrap">
          <h2 className="section-title">Chế độ chơi</h2>
          <span className="section-subtitle">Lựa chọn đấu trường xếp hạng hoặc rèn luyện kỹ năng</span>
        </div>
        <div className="modes-grid">
          <RankedModeCard />
          <AIModeCard />
        </div>
      </section>

      {/* 3. Unified Lobby Arena: Available Rooms (Main) + Online Players (Sidebar) */}
      <section className="arena-layout">
        {/* Main Content Area: Room Browser */}
        <div className="arena-main card">
          <header className="section-head">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <h2>Phòng thi đấu</h2>
              <span className="count">{rooms.length}</span>
            </div>

            <form
              className="inline-form"
              onSubmit={(e) => {
                e.preventDefault();
                run(() => store.createRoom(roomName, blockTwoEnds));
                setRoomName('');
              }}
            >
              <input
                placeholder="Tên phòng (mặc định: Phòng của bạn)"
                value={roomName}
                maxLength={40}
                onChange={(e) => setRoomName(e.target.value)}
              />
              <label className="check" title="5 quân bị chặn cả hai đầu không tính thắng">
                <input type="checkbox" checked={blockTwoEnds} onChange={(e) => setBlockTwoEnds(e.target.checked)} />
                Chặn 2 đầu
              </label>
              <button type="submit" className="primary" disabled={busy}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <line x1="12" y1="5" x2="12" y2="19" />
                  <line x1="5" y1="12" x2="19" y2="12" />
                </svg>
                Tạo phòng
              </button>
            </form>
          </header>

          {rooms.length === 0 ? (
            <div className="empty" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.75rem' }}>
              <div style={{
                width: '56px',
                height: '56px',
                borderRadius: '16px',
                background: 'var(--surface-well)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--text-dim)',
                border: '1px solid var(--border)'
              }}>
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <rect x="2" y="3" width="20" height="14" rx="2" />
                  <line x1="8" y1="21" x2="16" y2="21" />
                  <line x1="12" y1="17" x2="12" y2="21" />
                </svg>
              </div>
              <div>
                <p style={{ margin: 0, fontWeight: 600, color: 'var(--text-primary)', fontSize: '0.95rem' }}>Chưa có phòng nào đang mở</p>
                <p className="muted" style={{ margin: '0.25rem 0 0', fontSize: '0.85rem' }}>
                  Hãy đặt tên và nhấn <strong>"Tạo phòng"</strong> để mở phòng mới, hoặc bấm <strong>"Tìm trận ngay"</strong> để ghép đôi tự động.
                </p>
              </div>
            </div>
          ) : (
            <div className="table-responsive">
              <table>
                <thead>
                  <tr>
                    <th>Phòng đấu</th>
                    <th>Chủ phòng</th>
                    <th>Trạng thái</th>
                    <th className="num">Người chơi</th>
                    <th className="num">Khán giả</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {rooms.map((r) => (
                    <tr key={r.id}>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flexWrap: 'wrap' }}>
                          <strong style={{ color: 'var(--text-primary)' }}>{r.name}</strong>
                          <RoomBadges room={r} />
                        </div>
                      </td>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                          <span className="avatar-badge" style={{ width: '26px', height: '26px', fontSize: '0.72rem' }}>
                            {r.players[0]?.displayName.charAt(0).toUpperCase()}
                          </span>
                          <span>{T.player(r.players[0])}</span>
                        </div>
                      </td>
                      <td>
                        <span className={`badge status-${r.status}`}>{T.roomStatus[r.status]}</span>
                      </td>
                      <td className="num" style={{ fontWeight: 500 }}>{r.players.length}/2</td>
                      <td className="num">{r.spectators.length}</td>
                      <td className="right actions-cell">
                        {r.status === 'WAITING' && (
                          <button type="button" className="small primary" disabled={busy} onClick={() => run(() => store.joinRoom(r.id))}>
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
            </div>
          )}
        </div>

        {/* Sidebar: Online Players Activity Panel */}
        <aside className="arena-sidebar card">
          <header className="section-head" style={{ marginBottom: '0.75rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <h2>Người chơi online</h2>
              <span className="count">{online.length}</span>
            </div>
          </header>
          <ul className="people">
            {online.map((u) => {
              const isMe = u.id === me?.id;
              return (
                <li key={u.id} style={{ background: isMe ? 'var(--primary-subtle)' : undefined }}>
                  <span className="avatar-badge" style={{
                    borderColor: isMe ? 'var(--primary-border)' : undefined
                  }}>
                    {u.displayName.charAt(0).toUpperCase()}
                  </span>
                  <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0, flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                      <button type="button" className="link" onClick={() => modals.openProfile(u.id)} style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                        {u.displayName}
                      </button>
                      {isMe && <span className="badge tag" style={{ background: 'var(--primary-subtle)', color: 'var(--primary)' }}>Bạn</span>}
                    </div>
                    <span className="muted" style={{ fontSize: '0.8rem' }}>Elo {u.rating}</span>
                  </div>
                  <span className={`badge presence-${u.status}`}>{T.presence[u.status]}</span>
                  {!isMe && <InviteButton user={u} disabled={inQueue} />}
                </li>
              );
            })}
          </ul>
        </aside>
      </section>
    </main>
  );
}

function HeroBanner({ user }: { user: { displayName: string; username: string; rating: number } }) {
  return (
    <section className="hero-banner">
      <div className="hero-content">
        <div className="hero-title">
          <span>Xin chào,</span>
          <span className="hero-user">{user.displayName}</span>
          <span>👋</span>
        </div>
        <p className="hero-desc">
          Chào mừng đến với Caro Online. Tham gia leo thang xếp hạng Elo hoặc thử thách tư duy với trí tuệ nhân tạo.
        </p>
      </div>
      <div className="hero-stats">
        <div className="hero-stat-item">
          <span className="hero-stat-label">Hạng Elo hiện tại</span>
          <span className="hero-stat-val">{user.rating}</span>
        </div>
        <div style={{ width: '1px', height: '32px', background: 'var(--border)' }} />
        <div className="hero-stat-item">
          <span className="hero-stat-label">Tài khoản</span>
          <span style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text-primary)' }}>@{user.username}</span>
        </div>
      </div>
    </section>
  );
}

function RankedModeCard() {
  const { queue } = useStore();
  const [busy, run] = useAction();
  const inQueue = queue?.inQueue ?? false;
  const now = useServerNow(1000, inQueue);

  return (
    <div className={`mode-card mode-card-ranked ${inQueue ? 'searching' : ''}`}>
      <div className="mode-card-header">
        <div className="card-icon-box card-icon-ranked">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
          </svg>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
            <h3 style={{ fontSize: '1.05rem', color: 'var(--text-primary)' }}>Đấu Xếp Hạng (Ranked)</h3>
            {inQueue ? (
              <span className="badge presence-IN_QUEUE">Đang tìm đối thủ...</span>
            ) : (
              <span className="badge" style={{ background: 'var(--primary-subtle)', color: 'var(--primary)', borderColor: 'var(--primary-border)' }}>Tính điểm Elo</span>
            )}
          </div>
          <p className="muted" style={{ margin: '0.2rem 0 0', fontSize: '0.825rem', lineHeight: 1.4 }}>
            Ghép đôi tự động với người chơi có trình độ tương đương.
          </p>
        </div>
      </div>

      <div className="mode-card-body">
        {inQueue && queue ? (
          <div className="queue-status-box">
            <span>Đối thủ: <strong>{queue.ratingRange === null ? 'Bất kỳ ai' : `Elo ±${queue.ratingRange}`}</strong></span>
            <span>Chờ: <strong>{T.clock(now - (queue.since ?? now))}</strong> ({queue.queueSize} người)</span>
          </div>
        ) : (
          <div className="mode-info-tag">
            <span>⏱️ 30s/nước</span>
            <span>·</span>
            <span>🏆 Thắng +Elo / Thua -Elo</span>
          </div>
        )}
      </div>

      <div className="mode-card-footer">
        <button
          type="button"
          className={inQueue ? 'danger-solid' : 'primary'}
          disabled={busy}
          style={{ width: '100%', padding: '0.65rem 1rem' }}
          onClick={() => run(() => (inQueue ? store.cancelQuickMatch() : store.quickMatch()))}
        >
          {inQueue ? 'Hủy tìm trận' : 'Tìm trận ngay'}
        </button>
      </div>
    </div>
  );
}

function AIModeCard() {
  const [busy, run] = useAction();
  const [level, setLevel] = useState<BotLevel>('EASY');
  const [blockTwoEnds, setBlockTwoEnds] = useState(false);

  return (
    <div className="mode-card mode-card-ai">
      <div className="mode-card-header">
        <div className="card-icon-box card-icon-ai">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <rect x="3" y="11" width="18" height="10" rx="2" />
            <circle cx="12" cy="5" r="2" />
            <path d="M12 7v4" />
            <line x1="8" y1="16" x2="8" y2="16" />
            <line x1="16" y1="16" x2="16" y2="16" />
          </svg>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
            <h3 style={{ fontSize: '1.05rem', color: 'var(--text-primary)' }}>Chơi với Máy (AI Engine)</h3>
            <span className="badge" style={{ background: 'var(--accent-purple-subtle)', color: 'var(--accent-purple)', borderColor: 'var(--accent-purple-border)' }}>Luyện tập</span>
          </div>
          <p className="muted" style={{ margin: '0.2rem 0 0', fontSize: '0.825rem', lineHeight: 1.4 }}>
            Thuật toán Minimax. Trận đấu không tính Elo và không lưu lịch sử.
          </p>
        </div>
      </div>

      <div className="mode-card-body">
        <div className="ai-controls-row">
          <select value={level} onChange={(e) => setLevel(e.target.value as BotLevel)} aria-label="Mức độ" style={{ flex: 1 }}>
            <option value="EASY">Độ khó: Dễ (Tập sự)</option>
            <option value="HARD">Độ khó: Khó (Cao thủ)</option>
          </select>
          <label className="check" title="5 quân bị chặn hai đầu không tính thắng">
            <input type="checkbox" checked={blockTwoEnds} onChange={(e) => setBlockTwoEnds(e.target.checked)} />
            Chặn 2 đầu
          </label>
        </div>
      </div>

      <div className="mode-card-footer">
        <button
          type="button"
          className="primary-purple"
          disabled={busy}
          style={{ width: '100%', padding: '0.65rem 1rem' }}
          onClick={() => run(() => store.playBot(level, blockTwoEnds))}
        >
          Bắt đầu đấu máy
        </button>
      </div>
    </div>
  );
}

function RoomBadges({ room }: { room: RoomInfo }) {
  return (
    <>
      {room.botLevel && (
        <span className="badge tag" style={{ background: 'var(--accent-purple-subtle)', color: 'var(--accent-purple)', borderColor: 'var(--accent-purple-border)' }}>
          🤖 Máy ({T.botLevel[room.botLevel]})
        </span>
      )}
      {room.blockTwoEnds && (
        <span className="badge tag" style={{ background: 'var(--warning-subtle)', color: 'var(--warning)', borderColor: 'var(--warning-border)' }}>
          🛡️ Chặn 2 đầu
        </span>
      )}
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
      Mời đấu
    </button>
  );
}
