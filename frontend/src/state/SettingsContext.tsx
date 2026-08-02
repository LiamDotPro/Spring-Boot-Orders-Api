import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

export interface Settings {
  customerId: string;
  currency: string;
  /** Mirrors payments.auto-decline-above so the UI can predict the outcome. */
  declineAbove: number;
  polling: boolean;
}

const DEFAULTS: Settings = {
  customerId: 'cus-1',
  currency: 'GBP',
  declineAbove: 500,
  polling: true,
};

const STORAGE_KEY = 'orders-console.settings';

function load(): Settings {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored ? { ...DEFAULTS, ...(JSON.parse(stored) as Partial<Settings>) } : DEFAULTS;
  } catch {
    return DEFAULTS;
  }
}

interface SettingsContextValue {
  settings: Settings;
  update: (patch: Partial<Settings>) => void;
}

const SettingsContext = createContext<SettingsContextValue | null>(null);

/**
 * Kept above the router so the customer id and decline ceiling survive navigating
 * between the two pages, and a reload.
 */
export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<Settings>(load);

  const update = useCallback((patch: Partial<Settings>) => {
    setSettings((previous) => {
      const next = { ...previous, ...patch };
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      } catch {
        /* private browsing, quota — not worth failing the app over */
      }
      return next;
    });
  }, []);

  const value = useMemo(() => ({ settings, update }), [settings, update]);
  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>;
}

export function useSettings(): SettingsContextValue {
  const context = useContext(SettingsContext);
  if (!context) throw new Error('useSettings must be used inside SettingsProvider');
  return context;
}
