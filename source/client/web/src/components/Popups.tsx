import { store, useStore } from '../state/store';
import * as T from '../texts';
import { useAction, useServerNow } from './hooks';

/**
 * Invitations (received and sent) and short notices, in the bottom-right corner.
 * Invitations disappear when the server says so (INVITE_CLOSED), never on a local timer.
 */
export function Popups() {
  const { invites, outgoing, notices } = useStore();
  const now = useServerNow(500, invites.length > 0);
  const [busy, run] = useAction();
  return (
    <div className="popups" aria-live="polite">
      {invites.map((inv) => (
        <div key={inv.inviteId} className="popup invite">
          <p><strong>{inv.from.displayName}</strong> (Elo {inv.from.rating}) mời bạn chơi một ván.</p>
          <p className="muted">Hết hạn sau {T.clock(inv.expiresAt - now)}</p>
          <div className="actions">
            <button type="button" disabled={busy} onClick={() => run(() => store.answerInvite(inv.inviteId, false))}>
              Từ chối
            </button>
            <button type="button" className="primary" disabled={busy}
              onClick={() => run(() => store.answerInvite(inv.inviteId, true))}>
              Đồng ý
            </button>
          </div>
        </div>
      ))}
      {outgoing && (
        <div className="popup">
          <p>Đang chờ <strong>{outgoing.name}</strong> trả lời lời mời...</p>
          <div className="actions">
            <button type="button" onClick={() => store.run(() => store.cancelInvite())}>Hủy lời mời</button>
          </div>
        </div>
      )}
      {notices.map((n) => (
        <div key={n.id} className={`popup notice ${n.kind}`} onClick={() => store.dismiss(n.id)}>
          {n.text}
        </div>
      ))}
    </div>
  );
}
