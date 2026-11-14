import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Red confirm button, for actions that lose something (resigning, locking an account...). */
  danger?: boolean;
}

type Ask = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<Ask | null>(null);

/** Replacement for window.confirm(): `if (await confirm({...})) ...`, shown as the app's own small dialog. */
export function useConfirm(): Ask {
  const ask = useContext(ConfirmContext);
  if (!ask) throw new Error('useConfirm outside ConfirmProvider');
  return ask;
}

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<{ options: ConfirmOptions; resolve: (ok: boolean) => void } | null>(null);
  const ask = useCallback<Ask>((options) => new Promise((resolve) => setPending({ options, resolve })), []);
  const close = (ok: boolean) => {
    pending?.resolve(ok);
    setPending(null);
  };
  return (
    <ConfirmContext.Provider value={ask}>
      {children}
      {pending && <ConfirmDialog options={pending.options} onClose={close} />}
    </ConfirmContext.Provider>
  );
}

/** Esc or a click outside cancels; Enter confirms (the confirm button has the focus). */
function ConfirmDialog({ options, onClose }: { options: ConfirmOptions; onClose: (ok: boolean) => void }) {
  const confirmButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    confirmButton.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="overlay confirm-overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose(false)}>
      <div className="modal confirm" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title"
        aria-describedby="confirm-message">
        <div className="modal-body">
          <h2 id="confirm-title">{options.title}</h2>
          <p id="confirm-message">{options.message}</p>
          <div className="actions">
            <button type="button" onClick={() => onClose(false)}>{options.cancelLabel ?? 'Hủy'}</button>
            <button type="button" ref={confirmButton} className={options.danger ? 'danger-solid' : 'primary'}
              onClick={() => onClose(true)}>
              {options.confirmLabel ?? 'Đồng ý'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
