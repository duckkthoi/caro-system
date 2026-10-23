import { useEffect, useState } from 'react';

import { store } from '../state/store';

/**
 * Server time for countdowns. Read fresh on every render; while `active`, a timer re-renders the component
 * every `intervalMs` so the countdown moves.
 */
export function useServerNow(intervalMs = 250, active = true): number {
  const [, setTick] = useState(0);
  useEffect(() => {
    if (!active) {
      return undefined;
    }
    const timer = setInterval(() => setTick((t) => t + 1), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs, active]);
  return store.serverNow();
}

/** Loads data when the component appears and again when `deps` change. */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[]): { data: T | null; error: string | null; reload: () => void } {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setError(null);
    load()
      .then((d) => !cancelled && setData(d))
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : String(e)));
    return () => {
      cancelled = true;
    };
  }, [...deps, version]);
  return { data, error, reload: () => setVersion((v) => v + 1) };
}

/** Runs an async action, disabling the trigger while it runs; errors become notices. */
export function useAction(): [boolean, (action: () => Promise<unknown>) => void] {
  const [busy, setBusy] = useState(false);
  const run = (action: () => Promise<unknown>) => {
    setBusy(true);
    action()
      .catch((e) => store.notify(e instanceof Error ? e.message : String(e), 'error'))
      .finally(() => setBusy(false));
  };
  return [busy, run];
}
