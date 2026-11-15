import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';

import type { HistoryItem, LeaderboardEntry, MatchDetail, Page, Profile } from '../api/protocol';
import { rest } from '../api/rest';
import { positionAfter } from '../state/match';
import { useStore } from '../state/store';
import * as T from '../texts';
import { Board } from './Board';
import { useLoad } from './hooks';

type ModalState =
  | { kind: 'profile'; userId: number }
  | { kind: 'history'; userId: number; title: string }
  | { kind: 'replay'; matchId: string }
  | { kind: 'leaderboard' }
  | null;

interface Modals {
  openProfile(userId: number): void;
  openHistory(userId: number, title: string): void;
  openReplay(matchId: string): void;
  openLeaderboard(): void;
}

const ModalContext = createContext<Modals | null>(null);

export function useModals(): Modals {
  const modals = useContext(ModalContext);
  if (!modals) throw new Error('useModals outside ModalProvider');
  return modals;
}

export function ModalProvider({ children }: { children: ReactNode }) {
  const [stack, setStack] = useState<NonNullable<ModalState>[]>([]);
  const push = (m: NonNullable<ModalState>) => setStack((s) => [...s, m]);
  const pop = () => setStack((s) => s.slice(0, -1));
  const modals: Modals = {
    openProfile: (userId) => push({ kind: 'profile', userId }),
    openHistory: (userId, title) => push({ kind: 'history', userId, title }),
    openReplay: (matchId) => push({ kind: 'replay', matchId }),
    openLeaderboard: () => push({ kind: 'leaderboard' }),
  };
  const top = stack[stack.length - 1] ?? null;
  return (
    <ModalContext.Provider value={modals}>
      {children}
      {top?.kind === 'profile' && <ProfileModal userId={top.userId} onClose={pop} />}
      {top?.kind === 'history' && <HistoryModal userId={top.userId} title={top.title} onClose={pop} />}
      {top?.kind === 'replay' && <ReplayModal matchId={top.matchId} onClose={pop} />}
      {top?.kind === 'leaderboard' && <LeaderboardModal onClose={pop} />}
    </ModalContext.Provider>
  );
}

export function Modal({ title, onClose, children, wide }: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'wide' : ''}`} role="dialog" aria-label={title}>
        <header>
          <h2>{title}</h2>
          <button type="button" className="ghost" onClick={onClose} aria-label="Đóng">✕</button>
        </header>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

export function Pager({ page, onPage }: { page: Page<unknown>; onPage: (p: number) => void }) {
  const pages = Math.max(1, page.totalPages);
  return (
    <div className="pager">
      <button type="button" disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>‹ Trước</button>
      <span>Trang {page.page + 1}/{pages} · {page.totalItems} mục</span>
      <button type="button" disabled={page.page + 1 >= pages} onClick={() => onPage(page.page + 1)}>Sau ›</button>
    </div>
  );
}

function ProfileModal({ userId, onClose }: { userId: number; onClose: () => void }) {
  const { token } = useStore();
  const modals = useModals();
  const { data: p, error } = useLoad<Profile>(() => rest.profile(token!, userId), [userId]);
  return (
    <Modal title={p ? `Hồ sơ võ sĩ: ${p.player.displayName}` : 'Hồ sơ người chơi'} onClose={onClose}>
      {error && <p className="error">{error}</p>}
      {p && (
        <>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1.25rem' }}>
            <span className="avatar-badge" style={{ width: '48px', height: '48px', fontSize: '1.25rem', background: '#172554', borderColor: '#3b82f6', color: '#60a5fa' }}>
              {p.player.displayName.charAt(0).toUpperCase()}
            </span>
            <div>
              <h3 style={{ fontSize: '1.15rem', color: '#ffffff' }}>{p.player.displayName}</h3>
              <span className="muted">@{p.player.username} · {p.online ? '🟢 Đang trực tuyến' : '⚪ Đang Offline'}</span>
            </div>
          </div>
          <dl className="facts">
            <dt>Điểm Elo Xếp Hạng</dt><dd style={{ color: '#60a5fa' }}>{p.player.rating}</dd>
            <dt>Xếp Hạng Đấu Trường</dt><dd>{p.rank === null ? 'Chưa xếp hạng' : `Hạng #${p.rank}`}</dd>
            <dt>Ngày gia nhập</dt><dd>{T.date(p.joinedAt)}</dd>
          </dl>
          <div className="stats">
            <Stat label="Tổng Trận" value={p.stats.played} />
            <Stat label="Thắng" value={<span style={{ color: '#4ade80' }}>{p.stats.wins}</span>} />
            <Stat label="Thua" value={<span style={{ color: '#f87171' }}>{p.stats.losses}</span>} />
            <Stat label="Hòa" value={p.stats.draws} />
            <Stat label="Tỉ Lệ Thắng" value={<span style={{ color: '#60a5fa' }}>{`${p.stats.winRate}%`}</span>} />
          </div>
          <div className="actions">
            <button type="button" className="primary" onClick={() => modals.openHistory(userId, `Lịch sử đấu của ${p.player.displayName}`)}>
              Xem lịch sử đấu
            </button>
          </div>
        </>
      )}
    </Modal>
  );
}

export function Stat({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="stat">
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

function HistoryModal({ userId, title, onClose }: { userId: number; title: string; onClose: () => void }) {
  const { token } = useStore();
  const modals = useModals();
  const [page, setPage] = useState(0);
  const { data, error } = useLoad<Page<HistoryItem>>(() => rest.history(token!, userId, page), [userId, page]);
  return (
    <Modal title={title} onClose={onClose} wide>
      {error && <p className="error">{error}</p>}
      {data && data.items.length === 0 && <p className="muted empty">Chưa ghi nhận trận đấu nào.</p>}
      {data && data.items.length > 0 && (
        <>
          <table>
            <thead>
              <tr>
                <th>Thời gian</th><th>Đối thủ</th><th>Quân</th><th>Kết quả</th><th>Lý do kết thúc</th>
                <th className="num">Elo</th><th className="num">Số nước</th><th />
              </tr>
            </thead>
            <tbody>
              {data.items.map((h) => (
                <tr key={h.matchId}>
                  <td>{T.dateTime(h.endedAt)}</td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
                      <span className="avatar-badge" style={{ width: '24px', height: '24px', fontSize: '0.7rem' }}>
                        {h.opponent.displayName.charAt(0).toUpperCase()}
                      </span>
                      <span>{T.player(h.opponent)}</span>
                    </div>
                  </td>
                  <td><span className={`mark mark-${h.mark}`}>{h.mark}</span></td>
                  <td>
                    <span className={`badge ${h.outcome === 'WIN' ? 'status-WAITING' : h.outcome === 'LOSS' ? 'status-PLAYING' : ''}`}>
                      {T.outcome[h.outcome]}
                    </span>
                  </td>
                  <td className="muted">{T.reason[h.reason]}</td>
                  <td className="num" style={{ fontWeight: 600, color: h.ratingChange > 0 ? '#4ade80' : h.ratingChange < 0 ? '#f87171' : 'var(--text-muted)' }}>
                    {T.signed(h.ratingChange)}
                  </td>
                  <td className="num">{h.totalMoves}</td>
                  <td className="right">
                    <button type="button" className="small" onClick={() => modals.openReplay(h.matchId)}>
                      Xem lại ván
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={data} onPage={setPage} />
        </>
      )}
    </Modal>
  );
}

function ReplayModal({ matchId, onClose }: { matchId: string; onClose: () => void }) {
  const { token } = useStore();
  const { data, error } = useLoad<MatchDetail>(() => rest.match(token!, matchId), [matchId]);
  const [count, setCount] = useState<number | null>(null);
  const [playing, setPlaying] = useState(false);
  const total = data?.moves.length ?? 0;
  const shown = count ?? total;

  useEffect(() => {
    if (!playing) return undefined;
    if (shown >= total) {
      setPlaying(false);
      return undefined;
    }
    const timer = setTimeout(() => setCount(shown + 1), 700);
    return () => clearTimeout(timer);
  }, [playing, shown, total]);

  if (error) return <Modal title="Xem lại trận đấu" onClose={onClose}><p className="error">{error}</p></Modal>;
  if (!data) return <Modal title="Xem lại trận đấu" onClose={onClose}><p className="muted">Đang tải dữ liệu trận...</p></Modal>;

  const m = data.match;
  const last = shown > 0 ? data.moves[shown - 1] : null;
  return (
    <Modal title="Xem lại diễn biến trận đấu" onClose={onClose}>
      <p className="replay-head">
        <strong><span className="mark mark-X">X</span> {T.player(m.playerX)}</strong>
        <span className="muted"> vs </span>
        <strong><span className="mark mark-O">O</span> {T.player(m.playerO)}</strong>
      </p>
      <p className="muted" style={{ textAlign: 'center' }}>
        {T.result[m.result]} · {T.reason[m.reason]} · {T.dateTime(m.endedAt)}
        {m.blockTwoEnds && ` · ${T.BLOCK_TWO_ENDS}`}
      </p>
      <Board
        board={positionAfter(15, data.moves, shown)}
        lastMove={last ? [last.x, last.y] : null}
        winLine={shown === total ? data.winLine : null}
      />
      <input
        type="range" min={0} max={total} value={shown}
        onChange={(e) => { setPlaying(false); setCount(Number(e.target.value)); }}
        aria-label="Vị trí nước đi"
        style={{ marginTop: '0.75rem' }}
      />
      <div className="replay-controls">
        <button type="button" onClick={() => setCount(0)}>|‹</button>
        <button type="button" onClick={() => setCount(Math.max(0, shown - 1))}>‹</button>
        <span style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600 }}>Nước {shown}/{total}</span>
        <button type="button" onClick={() => setCount(Math.min(total, shown + 1))}>›</button>
        <button type="button" onClick={() => setCount(total)}>›|</button>
        <button
          type="button"
          className="primary"
          onClick={() => {
            if (!playing && shown >= total) setCount(0);
            setPlaying(!playing);
          }}
        >
          {playing ? 'Tạm dừng' : 'Tự động chạy'}
        </button>
      </div>
    </Modal>
  );
}

function LeaderboardModal({ onClose }: { onClose: () => void }) {
  const { token, me } = useStore();
  const modals = useModals();
  const [page, setPage] = useState(0);
  const { data, error } = useLoad<Page<LeaderboardEntry>>(() => rest.leaderboard(token!, page), [page]);

  const topRankColor = (rank: number) => {
    if (rank === 1) return { color: '#fbbf24', icon: '🥇' };
    if (rank === 2) return { color: '#94a3b8', icon: '🥈' };
    if (rank === 3) return { color: '#d97706', icon: '🥉' };
    return { color: 'var(--text-muted)', icon: `#${rank}` };
  };

  return (
    <Modal title="Bảng Vinh Danh Xếp Hạng Elo" onClose={onClose}>
      {error && <p className="error">{error}</p>}
      {data && (
        <>
          <table>
            <thead>
              <tr>
                <th className="num" style={{ width: '80px' }}>Thứ Hạng</th>
                <th>Người chơi</th>
                <th className="num">Điểm Elo</th>
                <th style={{ width: '40px' }} />
              </tr>
            </thead>
            <tbody>
              {data.items.map((e) => {
                const isMe = e.player.id === me?.id;
                const rankMeta = topRankColor(e.rank);
                return (
                  <tr key={e.player.id} className={isMe ? 'me' : ''}>
                    <td className="num">
                      <span style={{ fontWeight: 700, color: rankMeta.color, fontSize: e.rank <= 3 ? '1.1rem' : '0.875rem' }}>
                        {rankMeta.icon}
                      </span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <span className="avatar-badge" style={{
                          background: isMe ? '#1e3a8a' : undefined,
                          borderColor: isMe ? '#3b82f6' : undefined
                        }}>
                          {e.player.displayName.charAt(0).toUpperCase()}
                        </span>
                        <div>
                          <button type="button" className="link" onClick={() => modals.openProfile(e.player.id)} style={{ fontWeight: 600 }}>
                            {e.player.displayName}
                          </button>
                          <span className="muted"> @{e.player.username}</span>
                          {isMe && <span className="badge tag" style={{ background: '#1e3a8a', color: '#93c5fd' }}>Bạn</span>}
                        </div>
                      </div>
                    </td>
                    <td className="num" style={{ fontWeight: 700, color: '#60a5fa', fontSize: '0.95rem' }}>
                      {e.player.rating}
                    </td>
                    <td>{e.online && <span className="dot" title="Đang online" />}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <Pager page={data} onPage={setPage} />
        </>
      )}
    </Modal>
  );
}
