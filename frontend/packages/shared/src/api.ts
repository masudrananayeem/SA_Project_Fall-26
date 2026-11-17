/**
 * Thin client for the MeterFlow REST API, shared by the admin console and the customer portal.
 *
 * Local/dev: credentials are sent as HTTP Basic (the backend's demo users).
 * The X-Requested-With header tells Spring Security not to send a WWW-Authenticate challenge,
 * so a wrong password shows our own login error instead of the browser's native sign-in popup.
 */
const BASE = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');

export type Role = 'ADMIN' | 'CUSTOMER';

export interface Session {
  username: string;
  roles: Role[];
  customerId: string | null;
  /** Value of the Authorization header (Basic … or Bearer …). */
  authorization: string;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

let current: Session | null = null;
let onUnauthorized: (() => void) | null = null;

function storageKey(app: string) {
  return `meterflow.${app}.session`;
}

export function loadSession(app: string): Session | null {
  try {
    const raw = sessionStorage.getItem(storageKey(app));
    current = raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    current = null;
  }
  return current;
}

export function saveSession(app: string, session: Session | null) {
  current = session;
  try {
    if (session) sessionStorage.setItem(storageKey(app), JSON.stringify(session));
    else sessionStorage.removeItem(storageKey(app));
  } catch {
    /* storage unavailable: keep the session in memory only */
  }
}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export function apiBase() {
  return BASE;
}

async function parseError(res: Response): Promise<string> {
  const fallback =
    res.status === 401 ? 'Your session has expired. Please sign in again.'
    : res.status === 403 ? 'You do not have permission for this action.'
    : res.status === 404 ? 'Not found.'
    : `Request failed (${res.status})`;
  try {
    const text = await res.text();
    if (!text) return fallback;
    try {
      const body = JSON.parse(text);
      return body.detail || body.message || body.title || body.error || fallback;
    } catch {
      return text.length < 300 ? text : fallback;
    }
  } catch {
    return fallback;
  }
}

async function send(path: string, init: RequestInit = {}, authorization = current?.authorization): Promise<Response> {
  const headers: Record<string, string> = {
    'X-Requested-With': 'XMLHttpRequest',
    ...(init.body ? { 'Content-Type': 'application/json' } : {}),
    ...(authorization ? { Authorization: authorization } : {}),
    ...((init.headers as Record<string, string>) || {}),
  };
  let res: Response;
  try {
    res = await fetch(`${BASE}/api${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, 'Cannot reach the MeterFlow API. Is the backend running?');
  }
  if (!res.ok) {
    const message = await parseError(res);
    if (res.status === 401 && authorization && onUnauthorized) onUnauthorized();
    throw new ApiError(res.status, message);
  }
  return res;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await send(path, init);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function apiText(path: string): Promise<string> {
  const res = await send(path);
  return res.text();
}

export const get = <T>(path: string) => api<T>(path);
export const post = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
export const put = <T>(path: string, body: unknown) => api<T>(path, { method: 'PUT', body: JSON.stringify(body) });

/** Builds a query string, skipping empty values. */
export function qs(params: Record<string, string | number | boolean | null | undefined>) {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  const s = p.toString();
  return s ? `?${s}` : '';
}

/** Verifies credentials against GET /api/me and returns the session (not yet stored). */
export async function signIn(username: string, password: string): Promise<Session> {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  const authorization = 'Basic ' + btoa(String.fromCharCode(...bytes));
  const res = await send('/me', {}, authorization).catch((e: ApiError) => {
    if (e.status === 401) throw new ApiError(401, 'Incorrect username or password.');
    throw e;
  });
  const me = (await res.json()) as { username: string; roles: Role[]; customerId: string | null };
  return { ...me, authorization };
}

/** Public self-service sign-up (customer portal). Does not sign in. */
export async function signUp(input: { username: string; password: string; displayName: string; email: string; company?: string }) {
  const res = await send('/auth/signup', { method: 'POST', body: JSON.stringify(input) }, '');
  return (await res.json()) as { username: string; customerId: string };
}
