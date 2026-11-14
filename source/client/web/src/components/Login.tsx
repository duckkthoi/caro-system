import { useState, useEffect } from 'react';
import { useTheme } from '../state/theme';
import type { FormEvent } from 'react';

import { errorMessage } from '../api/protocol';
import { store, useStore } from '../state/store';

export function Login() {
  const { setTheme, theme } = useTheme();
  useEffect(() => {
    const previous = theme;
    setTheme('light');
    return () => setTheme(previous);
  }, []);
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
    <main className="login" data-theme="light">
      <form className="card login-card" onSubmit={submit}>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
          <div style={{
            width: '48px',
            height: '48px',
            borderRadius: '12px',
            background: 'var(--primary-subtle)',
            color: 'var(--primary)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: 'var(--shadow-xs)'
          }}>
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <rect x="3" y="3" width="18" height="18" rx="4" />
              <path d="M3 9h18M3 15h18M9 3v18M15 3v18" opacity="0.6" />
            </svg>
          </div>
          <h1>Caro Online</h1>
          <p className="muted">Cờ ca rô 15×15 đối kháng trực tuyến</p>
        </div>
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
