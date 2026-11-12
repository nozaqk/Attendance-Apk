/** Proxy, client-IP and CORS helpers shared by the server bootstrap and the routes. */

const IPV4 = /^(?:\d{1,3}\.){3}\d{1,3}$/;

export function isIpV4(value: string): boolean {
  if (!IPV4.test(value)) return false;
  return value.split(".").every((octet) => Number(octet) <= 255);
}

/** Loose but safe IPv6 check (also accepts IPv4-mapped addresses like ::ffff:10.0.0.1). */
export function isIpV6(value: string): boolean {
  if (!value.includes(":")) return false;
  const mapped = /^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/i.exec(value);
  if (mapped) return isIpV4(mapped[1]);
  if (!/^[0-9a-fA-F:]+$/.test(value)) return false;
  if (value.includes(":::")) return false;
  return (value.match(/:/g) ?? []).length >= 2;
}

export function isIpAddress(value: unknown): value is string {
  if (typeof value !== "string") return false;
  const v = value.trim();
  if (v.length === 0 || v.length > 64) return false;
  return isIpV4(v) || isIpV6(v);
}

type HeaderBag = Record<string, string | string[] | undefined>;

function header(headers: HeaderBag, name: string): string | undefined {
  const raw = headers[name] ?? headers[name.toLowerCase()];
  if (Array.isArray(raw)) return raw.join(",");
  return raw;
}

/** Strips brackets / ports that a proxy may append to the address it recorded. */
function cleanAddress(raw: string): string {
  const v = raw.trim();
  if (v.startsWith("[")) {
    const end = v.indexOf("]");
    return end > 0 ? v.slice(1, end) : v;
  }
  // "1.2.3.4:5678" but not a bare IPv6 address
  if (isIpV4(v.split(":")[0] ?? "") && v.includes(":") && !isIpV6(v)) return v.split(":")[0];
  return v;
}

export interface ClientIpOptions {
  /**
   * Read Cloudflare's `CF-Connecting-IP` first. Cloudflare overwrites that header on every
   * request it proxies, so it is the browser's real address - provided the service cannot be
   * reached directly (keep Render behind the Cloudflare proxy / block non-Cloudflare traffic).
   */
  trustCfConnectingIp?: boolean;
}

/**
 * The address a request really came from.
 *
 * Render terminates TLS in front of the app, so the socket peer is always a proxy. Order:
 *   1. Cloudflare's `CF-Connecting-IP` (set by the edge, cannot be forged while proxied)
 *   2. Cloudflare's `True-Client-IP` (Enterprise alias)
 *   3. the right-most valid `X-Forwarded-For` entry - proxies append, so the last entry is the
 *      one our trusted edge wrote; a client cannot control what comes after its own address
 *   4. `X-Real-IP`
 *   5. the socket address
 */
export function clientIpOf(
  headers: HeaderBag,
  fallback: string,
  opts: ClientIpOptions = {},
): string {
  if (opts.trustCfConnectingIp !== false) {
    for (const name of ["cf-connecting-ip", "true-client-ip"]) {
      const candidate = cleanAddress(header(headers, name) ?? "");
      if (isIpAddress(candidate)) return candidate;
    }
  }
  const forwarded = header(headers, "x-forwarded-for");
  if (forwarded) {
    const parts = forwarded.split(",").map((s) => cleanAddress(s)).filter(Boolean);
    for (let i = parts.length - 1; i >= 0; i -= 1) {
      if (isIpAddress(parts[i])) return parts[i];
    }
  }
  const realIp = cleanAddress(header(headers, "x-real-ip") ?? "");
  if (isIpAddress(realIp)) return realIp;
  return fallback;
}

/** Normalises `https://a.app/` to `https://a.app`; returns null for anything that is not an http(s) origin. */
export function normalizeOrigin(value: string): string | null {
  const v = value.trim().replace(/\/+$/, "");
  if (!v || v === "*") return null;
  try {
    const url = new URL(v);
    if (url.protocol !== "http:" && url.protocol !== "https:") return null;
    return url.origin.toLowerCase();
  } catch {
    return null;
  }
}

function wildcardPattern(entry: string): RegExp | null {
  const v = entry.trim().replace(/\/+$/, "").toLowerCase();
  if (!v.includes("*")) return null;
  const escaped = v.replace(/[.+?^${}()|[\]\\]/g, "\\$&").replace(/\*/g, "[^.]+");
  try {
    return new RegExp(`^${escaped}$`);
  } catch {
    return null;
  }
}

export interface OriginMatcherOptions {
  /** When no Origin header is sent (native app, curl, server-to-server) allow the request. */
  allowMissingOrigin?: boolean;
}

/**
 * Builds a predicate for `@fastify/cors`'s `origin` delegate.
 * - explicit list of origins, `https://*.example.com` wildcards supported
 * - `*` allows everything (development only; `loadConfig` already refuses it in production)
 * - requests without an `Origin` header are allowed, because browsers always send one while the
 *   Android app and the ESP32 nodes never do
 */
export function createOriginMatcher(
  origins: string[],
  opts: OriginMatcherOptions = {},
): (origin: string | undefined) => boolean {
  const allowMissing = opts.allowMissingOrigin !== false;
  if (origins.some((o) => o.trim() === "*")) return () => true;

  const exact = new Set<string>();
  const patterns: RegExp[] = [];
  for (const raw of origins) {
    if (!raw || !raw.trim()) continue;
    const pattern = wildcardPattern(raw);
    if (pattern) {
      patterns.push(pattern);
      continue;
    }
    const normalized = normalizeOrigin(raw);
    if (normalized) exact.add(normalized);
  }

  return (origin) => {
    if (origin === undefined || origin === "") return allowMissing;
    // "null" is what sandboxed iframes and file:// pages send - never a real frontend.
    if (origin === "null") return false;
    const normalized = normalizeOrigin(origin);
    if (!normalized) return false;
    if (exact.has(normalized)) return true;
    return patterns.some((p) => p.test(normalized));
  };
}
