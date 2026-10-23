import { useEffect, useState } from 'react';

import { AdminPage } from './admin/AdminPage';
import { Lobby } from './components/Lobby';
import { Login } from './components/Login';
import { ModalProvider, useModals } from './components/Modals';
import { Popups } from './components/Popups';
import { Room } from './components/Room';
import type { ConnectionStatus } from './state/store';
import { store, useStore } from './state/store';

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
    <ModalProvider>
      <Screens />
      <Popups />
    </ModalProvider>
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
  if (!me) return null;
  return (
    <nav className="topbar">
      <span className="brand">Caro Online</span>
      {!room && me.role === 'ADMIN' && (
        <div className="tabs">
          <button type="button" className={!admin ? 'active' : ''} onClick={() => onAdmin(false)}>Sảnh</button>
          <button type="button" className={admin ? 'active' : ''} onClick={() => onAdmin(true)}>Quản trị</button>
        </div>
      )}
      <span className="spacer" />
      <span className={`conn conn-${connection}`}>{connectionText[connection]}</span>
      <button type="button" className="link me" onClick={() => modals.openProfile(me.id)}>
        {me.displayName} · <strong>{me.rating}</strong>
      </button>
      <button type="button" onClick={() => modals.openHistory(me.id, 'Lịch sử đấu của bạn')}>Lịch sử</button>
      <button type="button" onClick={modals.openLeaderboard}>Bảng xếp hạng</button>
      <button type="button" onClick={() => store.logout()}>Đăng xuất</button>
    </nav>
  );
}
