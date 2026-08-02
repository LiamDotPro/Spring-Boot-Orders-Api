import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

export type LogKind = 'info' | 'ok' | 'bad';

export interface LogEntry {
  id: number;
  at: Date;
  message: string;
  kind: LogKind;
  /** Milliseconds since the order was accepted by the API, when that is meaningful. */
  elapsedMs?: number;
}

interface TransitionLogValue {
  entries: LogEntry[];
  log: (message: string, kind?: LogKind, elapsedMs?: number) => void;
  clear: () => void;
}

const TransitionLogContext = createContext<TransitionLogValue | null>(null);

const MAX_ENTRIES = 200;
let nextId = 0;

/**
 * Lives above the router so the log survives moving between pages — the whole point
 * is watching transitions land while you are doing something else.
 */
export function TransitionLogProvider({ children }: { children: ReactNode }) {
  const [entries, setEntries] = useState<LogEntry[]>([]);

  const log = useCallback((message: string, kind: LogKind = 'info', elapsedMs?: number) => {
    setEntries((previous) =>
      [{ id: nextId++, at: new Date(), message, kind, elapsedMs }, ...previous].slice(0, MAX_ENTRIES),
    );
  }, []);

  const clear = useCallback(() => setEntries([]), []);

  const value = useMemo(() => ({ entries, log, clear }), [entries, log, clear]);
  return <TransitionLogContext.Provider value={value}>{children}</TransitionLogContext.Provider>;
}

export function useTransitionLog(): TransitionLogValue {
  const context = useContext(TransitionLogContext);
  if (!context) throw new Error('useTransitionLog must be used inside TransitionLogProvider');
  return context;
}
