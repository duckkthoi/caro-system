import { useState } from 'react';
import type { FormEvent } from 'react';

import { errorMessage } from '../api/protocol';
import { store, useStore } from '../state/store';

export function Login() {
  const { logoutReason, connection } = useStore();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState(() => localStorage.getItem('caro.username') ?? '');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'login') {
        await store.login(username.trim(), password);
      } else {
        await store.register(username.trim(), password, displayName);
      }
      localStorage.setItem('caro.username', username.trim());
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="login">
      <form className="card login-card" onSubmit={submit}>
        <h1>Caro Online</h1>
        <p className="muted">Cờ ca rô 15×15 · chơi qua WebSocket</p>
        {logoutReason && <p className="banner error">{logoutReason}</p>}
        <div className="tabs">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
            Đăng nhập
          </button>
          <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>
            Đăng ký
          </button>
        </div>
        <label>
          Tên đăng nhập
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required autoFocus />
        </label>
        <label>
          Mật khẩu
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            required
          />
        </label>
        {mode === 'register' && (
          <>
            <label>
              Tên hiển thị <span className="muted">(không bắt buộc)</span>
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={30} />
            </label>
            <p className="hint">Username 3–20 ký tự a-z, 0-9, _. Mật khẩu ít nhất 6 ký tự.</p>
          </>
        )}
        {error && <p className="error">{error}</p>}
        <button type="submit" className="primary" disabled={busy || connection === 'connecting'}>
          {busy ? 'Đang xử lý...' : mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}
        </button>
      </form>
    </main>
  );
}
