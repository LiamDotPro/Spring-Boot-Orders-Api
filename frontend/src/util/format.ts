export function money(value: number | null | undefined, currency?: string): string {
  if (value == null || !Number.isFinite(value)) return '—';
  return `${value.toFixed(2)} ${currency ?? ''}`.trim();
}

export function shortId(id: string | null | undefined): string {
  return (id ?? '').slice(0, 8);
}

export function humanDuration(ms: number): string {
  if (!Number.isFinite(ms) || ms < 0) return '—';
  if (ms < 1000) return `${Math.round(ms)}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m`;
  return `${Math.floor(ms / 3_600_000)}h`;
}

export function clockTime(date: Date = new Date()): string {
  return `${date.toTimeString().slice(0, 8)}.${String(date.getMilliseconds()).padStart(3, '0')}`;
}

export function ageOf(isoTimestamp: string | null | undefined): number {
  const parsed = Date.parse(isoTimestamp ?? '');
  return Number.isNaN(parsed) ? 0 : Date.now() - parsed;
}
