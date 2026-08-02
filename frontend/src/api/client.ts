import type { ProblemDetail } from './types';

/**
 * A non-2xx response, carrying the parsed ProblemDetail so callers can read the
 * extension members (sku / requested / available) instead of parsing the sentence.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = { detail: text.slice(0, 300) };
    }
  }

  if (!response.ok) throw new ApiError(response.status, body as ProblemDetail | null);
  return body as T;
}

/** Human-readable text for anything thrown by a query or mutation. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const { title, detail } = error.problem ?? {};
    if (title && detail && title !== detail) return `${title}: ${detail}`;
    return detail ?? title ?? `HTTP ${error.status}`;
  }
  if (error instanceof Error) return error.message;
  return 'Network error — is the API running?';
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}
