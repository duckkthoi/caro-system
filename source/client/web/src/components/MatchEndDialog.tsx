import { useEffect, useRef } from 'react';

import { endKind, myRatingChange, opponentOf } from '../state/match';
import { latestPlayer, store, useStore } from '../state/store';
import * as T from '../texts';
import { useAction } from './hooks';

/**
 * The result of the match that just ended, in the middle of the screen. Only opened by a live MATCH_END
 * (store.endedMatchId), never by a resync of a match that was already over.
 * Closes itself when a new match starts (rematch) or when I leave the room.
 */
export function MatchEndDialog() {
  const { endedMatchId, match, room, me } = useStore();
  const [busy, run] = useAction();
  const mainButton = useRef<HTMLButtonElement>(null);
  const open = endedMatchId !== null && match?.matchId === endedMatchId && match.result !== null && room && me;

  useEffect(() => {
    if (!open) return undefined;
    mainButton.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && store.closeMatchEnd();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  if (!open) return null;
  const result = match.result!;
  const kind = endKind(match, result);
  const watching = kind === 'WATCHED' || match.myMark === null;
  const title = kind === 'WATCHED' ? `${T.result[result.result]}!` : T.endTitle[kind];

  const change = myRatingChange(match, result);
  const opponent = watching ? null : latestPlayer(room, opponentOf(match));
  const opponentLeft = opponent !== null && !room.players.some((p) => p.id === opponent.id);
  const canRematch = !watching && kind !== 'ABORTED' && room.status === 'FINISHED' && !opponentLeft;
  const askedRematch = room.rematchRequests.includes(me.id);
  const opponentAsked = opponent !== null && room.rematchRequests.includes(opponent.id);

  const close = () => store.closeMatchEnd();
  const leave = () => run(() => store.leaveRoom());
  const rematch = () => run(() => store.rematch());

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && close()}>
      <div className={`modal match-end match-end-${kind}`} role="alertdialog" aria-modal="true"
        aria-labelledby="match-end-title" aria-describedby="match-end-detail">
        <div className="modal-body">
          <div className="match-end-icon" aria-hidden="true">{T.endIcon[kind]}</div>
          <h2 id="match-end-title">{title}</h2>
          <p id="match-end-detail" className="match-end-reason">
            {T.reason[result.reason]}
            {kind === 'ABORTED' && ' · Không tính điểm'}
          </p>

          {watching ? (
            <p className="muted">{match.playerX.displayName} (X) gặp {match.playerO.displayName} (O)</p>
          ) : (
            <p className="muted">Đối thủ: {opponent ? opponent.displayName : '?'}</p>
          )}

          {!watching && match.rated && kind !== 'ABORTED' && (
            <div className="match-end-elo">
              <span className={change > 0 ? 'up' : change < 0 ? 'down' : ''}>{T.signed(change)} Elo</span>
              <span className="muted">Điểm hiện tại: {me.rating}</span>
            </div>
          )}

          {opponentLeft && <p className="match-end-note">Đối thủ đã rời phòng.</p>}
          {canRematch && opponentAsked && !askedRematch && (
            <p className="match-end-note">Đối thủ muốn tái đấu!</p>
          )}

          <div className="actions">
            <button type="button" onClick={close} ref={canRematch ? undefined : mainButton}>Xem bàn cờ</button>
            <button type="button" disabled={busy} onClick={leave}>{watching ? 'Thôi xem' : 'Rời phòng'}</button>
            {canRematch && (
              <button type="button" className="primary" ref={mainButton} disabled={busy || askedRematch}
                onClick={rematch}>
                {askedRematch ? 'Đang chờ đối thủ...' : 'Tái đấu (Đổi quân)'}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
