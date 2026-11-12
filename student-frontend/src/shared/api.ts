// Shared API client (copied into each frontend by scripts/sync-frontend-shared.sh — edit it there).

/**
 * API base URL — configured at build time, never hardcoded in components.
 *
 * Set `VITE_API_URL` (preferred) in `.env.production` / `.env.local`, e.g.
 *   VITE_API_URL=https://yourdomain.com
 * The legacy name `VITE_API_BASE_URL` is still honoured so existing deploys
 * keep working. Trailing slashes are stripped; an empty value means "same
 * origin" (useful when a dev server proxies /api).
 *
 * This value is public by definition (it ships in the JS bundle) — never put
 * secrets in VITE_* variables.
 */
const env = (import.meta as any).env ?? {};
const RAW_BASE: unknown = env.VITE_API_URL ?? env.VITE_API_BASE_URL ?? "";
export const API_BASE_URL: string = String(RAW_BASE).trim().replace(/\/+$/, "");

if (API_BASE_URL.includes("yourdomain.com")) {
  // Fail loudly in the console instead of silently calling a placeholder host.
  console.error(
    "[config] VITE_API_URL still points at the placeholder domain https://yourdomain.com — set your real API domain before deploying.",
  );
}

const BASE = API_BASE_URL;
const TOKEN_KEY = "attendance.token";

export function getToken(): string | null {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}
export function setToken(t: string | null) {
  try { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY); } catch { /* storage unavailable */ }
}

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details?: unknown) { super(message); }
}

let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: (() => void) | null) { onUnauthorized = fn; }

export async function api<T = any>(method: string, path: string, body?: unknown): Promise<T> {
  if (!BASE) throw new ApiError(0, "NO_API", "VITE_API_URL is not configured for this build");
  let res: Response;
  try {
    res = await fetch(BASE + path, {
      method,
      headers: { ...(body ? { "Content-Type": "application/json" } : {}), ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "NETWORK", "Cannot reach the server. Check your internet connection and try again.");
  }
  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const e = data?.error;
    if (res.status === 401 && getToken() && e?.code !== "INVALID_CREDENTIALS") onUnauthorized?.();
    throw new ApiError(res.status, e?.code ?? "ERROR", e?.message ?? `Request failed (${res.status})`, e?.details);
  }
  return data as T;
}
export const get = <T = any>(p: string) => api<T>("GET", p);
export const post = <T = any>(p: string, b?: unknown) => api<T>("POST", p, b ?? {});
export const put = <T = any>(p: string, b?: unknown) => api<T>("PUT", p, b ?? {});
export const del = <T = any>(p: string) => api<T>("DELETE", p);

/** Downloads an authenticated file (CSV/XLSX export) through the browser. */
export async function download(path: string, fallbackName: string) {
  const res = await fetch(BASE + path, { headers: { Authorization: `Bearer ${getToken()}` } });
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    throw new ApiError(res.status, data?.error?.code ?? "ERROR", data?.error?.message ?? "Export failed");
  }
  const cd = res.headers.get("Content-Disposition") ?? "";
  const name = /filename="?([^"]+)"?/.exec(cd)?.[1] ?? fallbackName;
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
