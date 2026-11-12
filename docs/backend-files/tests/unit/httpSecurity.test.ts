import { describe, expect, it, afterAll } from "vitest";
import { buildApp } from "../../src/app.js";
import { loadConfig } from "../../src/config.js";

const cfg = loadConfig({
  NODE_ENV: "development",
  DATABASE_URL: "postgres://x/y",
  JWT_SECRET: "s".repeat(40),
  CORS_ORIGINS: "https://attendance.example.com",
  RATE_LIMIT_MAX: "3",
  TRUST_PROXY_HOPS: "1",
  TRUST_CF_CONNECTING_IP: "true",
  LOG_LEVEL: "silent",
} as Record<string, string>);

const appPromise = buildApp({ config: cfg, prisma: {} as never, logger: false });
afterAll(async () => { (await appPromise).close(); });

/**
 * Wiring checks that need no database: CORS allow-list, no-store headers, client-IP based
 * rate limiting and the shared error envelope. `buildApp` receives a stub Prisma client.
 */
describe("http security wiring", () => {
  it("sets no-store on API responses", async () => {
    const app = await appPromise;
    const res = await app.inject({ method: "GET", url: "/health" });
    expect(res.statusCode).toBe(200);
    expect(res.headers["cache-control"]).toBe("no-store");
  });

  it("answers the preflight for an allowed origin only", async () => {
    const app = await appPromise;
    const ok = await app.inject({
      method: "OPTIONS",
      url: "/api/auth/login",
      headers: { origin: "https://attendance.example.com", "access-control-request-method": "POST" },
    });
    expect(ok.statusCode).toBe(204);
    expect(ok.headers["access-control-allow-origin"]).toBe("https://attendance.example.com");

    const bad = await app.inject({
      method: "OPTIONS",
      url: "/api/auth/login",
      headers: { origin: "https://evil.example.com", "access-control-request-method": "POST" },
    });
    expect(bad.headers["access-control-allow-origin"]).toBeUndefined();
  });

  it("still serves requests with no Origin header (Android app)", async () => {
    const app = await appPromise;
    const res = await app.inject({
      method: "POST",
      url: "/api/public/departments",
      headers: { "cf-connecting-ip": "203.0.113.99" },
    });
    expect(res.statusCode).not.toBe(403);
    expect(res.headers["access-control-allow-origin"]).toBeUndefined();
  });

  it("returns the shared error envelope when the limit is hit", async () => {
    const app = await appPromise;
    const codes: number[] = [];
    let last: { body: string } | undefined;
    const limit = cfg.RATE_LIMIT_MAX;
    for (let i = 0; i < limit + 2; i += 1) {
      const res = await app.inject({ method: "GET", url: "/health", headers: { "cf-connecting-ip": "41.66.10.9" } });
      codes.push(res.statusCode);
      last = res;
    }
    expect(codes).toContain(429);
    expect(JSON.parse(last!.body)).toMatchObject({ error: { code: "RATE_LIMITED" } });
  });

  it("buckets rate limits per client IP, not per proxy", async () => {
    const app = await appPromise;
    const other: number[] = [];
    for (let i = 0; i < cfg.RATE_LIMIT_MAX; i += 1) {
      const res = await app.inject({ method: "GET", url: "/health", headers: { "cf-connecting-ip": "203.0.113.50" } });
      other.push(res.statusCode);
    }
    expect(other.every((code) => code === 200)).toBe(true);
    // A spoofed X-Forwarded-For must not hand the throttled client a fresh bucket.
    const spoofed = await app.inject({
      method: "GET",
      url: "/health",
      headers: { "cf-connecting-ip": "41.66.10.9", "x-forwarded-for": "10.9.9.9" },
    });
    expect(spoofed.statusCode).toBe(429);
  });
});
