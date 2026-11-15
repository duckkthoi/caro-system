import { useEffect, useState } from 'react';

import { AdminPage } from './admin/AdminPage';
import { ConfirmProvider } from './components/Confirm';
import { Lobby } from './components/Lobby';
import { Login } from './components/Login';
import { ModalProvider, useModals } from './components/Modals';
import { Popups } from './components/Popups';
import { Room } from './components/Room';
import type { ConnectionStatus } from './state/store';
import { store, useStore } from './state/store';
import { ThemeProvider, useTheme } from './state/theme';

const connectionText: Record<ConnectionStatus, string> = {
  disconnected: 'Chưa kết nối',
  connecting: 'Đang kết nối...',
  connected: 'Đã kết nối',
  reconnecting: 'Mất kết nối, đang kết nối lại...',
};

/** Screens: login → lobby (or admin) → room. Being in a room always shows the room. */
export function App() {
  useEffect(() => {
    store.restore();
  }, []);
  return (
    <ThemeProvider>
      <ConfirmProvider>
        <ModalProvider>
          <Screens />
          <Popups />
        </ModalProvider>
      </ConfirmProvider>
    </ThemeProvider>
  );
}

function Screens() {
  const { me, room } = useStore();
  const [admin, setAdmin] = useState(false);
  if (!me) return <Login />;
  return (
    <>
      <TopBar admin={admin} onAdmin={setAdmin} />
      {room ? <Room /> : admin && me.role === 'ADMIN' ? <AdminPage /> : <Lobby />}
    </>
  );
}

function TopBar({ admin, onAdmin }: { admin: boolean; onAdmin: (on: boolean) => void }) {
  const { me, connection, room } = useStore();
  const modals = useModals();
  const { theme, toggleTheme } = useTheme();
  if (!me) return null;
  return (
    <nav className="topbar">
      <div className="brand">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <rect x="3" y="3" width="18" height="18" rx="4" />
          <path d="M3 9h18M3 15h18M9 3v18M15 3v18" opacity="0.6" />
        </svg>
        <span>Caro Online</span>
      </div>
      {!room && me.role === 'ADMIN' && (
        <div className="tabs">
          <button type="button" className={!admin ? 'active' : ''} onClick={() => onAdmin(false)}>Sảnh</button>
          <button type="button" className={admin ? 'active' : ''} onClick={() => onAdmin(true)}>Quản trị</button>
        </div>
      )}
      <span className="spacer" />
      <span className={`conn conn-${connection}`}>
        <span className="dot" style={{ backgroundColor: connection === 'connected' ? 'var(--success)' : connection === 'reconnecting' ? 'var(--danger)' : 'var(--text-dim)', margin: 0 }} />
        {connectionText[connection]}
      </span>
      <button
        type="button"
        className="theme-toggle-btn"
        onClick={toggleTheme}
        title={theme === 'dark' ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'}
        aria-label="Đổi giao diện sáng/tối"
      >
        {theme === 'dark' ? (
          <>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <circle cx="12" cy="12" r="5" />
              <line x1="12" y1="1" x2="12" y2="3" />
              <line x1="12" y1="21" x2="12" y2="23" />
              <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
              <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
              <line x1="1" y1="12" x2="3" y2="12" />
              <line x1="21" y1="12" x2="23" y2="12" />
              <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
              <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
            </svg>
            <span>Sáng</span>
          </>
        ) : (
          <>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
            </svg>
            <span>Tối</span>
          </>
        )}
      </button>
      <button type="button" className="link me-pill" onClick={() => modals.openProfile(me.id)}>
        <span className="avatar-badge" aria-hidden="true">
          {me.displayName ? me.displayName.charAt(0).toUpperCase() : me.username.charAt(0).toUpperCase()}
        </span>
        <span>{me.displayName}</span>
        <span className="muted">·</span>
        <strong>{me.rating}</strong>
      </button>
      <button type="button" onClick={() => modals.openHistory(me.id, 'Lịch sử đấu của bạn')}>Lịch sử</button>
      <button type="button" onClick={modals.openLeaderboard}>Bảng xếp hạng</button>
      <button type="button" className="danger" onClick={() => store.logout()}>Đăng xuất</button>
    </nav>
  );
}
