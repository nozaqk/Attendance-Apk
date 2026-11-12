import { describe, expect, it } from "vitest";
import { clientIpOf, createOriginMatcher, isIpAddress, normalizeOrigin } from "../../src/utils/network.js";
import { loadConfig } from "../../src/config.js";

const cf = { "cf-connecting-ip": "41.66.10.9" };
const xff = { "x-forwarded-for": "203.0.113.7" };

describe("client address resolution", () => {
  it("accepts IPv4 and IPv6, rejects junk", () => {
    expect(isIpAddress("41.66.10.9")).toBe(true);
    expect(isIpAddress("999.1.1.1")).toBe(false);
    expect(isIpAddress("::1")).toBe(true);
    expect(isIpAddress("::ffff:41.66.10.9")).toBe(true);
    expect(isIpAddress("not-an-ip")).toBe(false);
    expect(isIpAddress("")).toBe(false);
    expect(isIpAddress(undefined)).toBe(false);
  });

  it("prefers Cloudflare's CF-Connecting-IP", () => {
    expect(clientIpOf({ ...cf, ...xff }, "10.0.0.1")).toBe("41.66.10.9");
  });

  it("ignores a spoofed CF-Connecting-IP when Cloudflare is not trusted", () => {
    expect(clientIpOf({ ...cf, ...xff }, "10.0.0.1", { trustCfConnectingIp: false })).toBe("203.0.113.7");
  });

  it("ignores a malformed CF-Connecting-IP", () => {
    expect(clientIpOf({ "cf-connecting-ip": "attacker", ...xff }, "10.0.0.1")).toBe("203.0.113.7");
  });

  it("takes the right-most X-Forwarded-For entry, so a client cannot forge its bucket", () => {
    expect(clientIpOf({ "x-forwarded-for": "1.2.3.4, 41.66.10.9" }, "10.0.0.1")).toBe("41.66.10.9");
    expect(clientIpOf({ "x-forwarded-for": "1.2.3.4, 5.6.7.8, 41.66.10.9" }, "10.0.0.1")).toBe("41.66.10.9");
  });

  it("skips empty and malformed proxy entries", () => {
    expect(clientIpOf({ "x-forwarded-for": "garbage, 41.66.10.9, " }, "10.0.0.1")).toBe("41.66.10.9");
  });

  it("strips the port a proxy may append", () => {
    expect(clientIpOf({ "x-forwarded-for": "41.66.10.9:51234" }, "10.0.0.1")).toBe("41.66.10.9");
  });

  it("falls back to the socket address when no header is trustworthy", () => {
    expect(clientIpOf({}, "10.0.0.1")).toBe("10.0.0.1");
    expect(clientIpOf({ "x-real-ip": "41.66.10.9" }, "10.0.0.1")).toBe("41.66.10.9");
  });
});

describe("CORS origin matching", () => {
  const allowed = createOriginMatcher([
    "https://attendance.example.com",
    "https://lecturer.example.com/",
    "https://*.vercel.app",
  ]);

  it("allows the listed web origins", () => {
    expect(allowed("https://attendance.example.com")).toBe(true);
    expect(allowed("https://lecturer.example.com")).toBe(true);
    expect(allowed("https://ATTENDANCE.example.com")).toBe(true);
  });

  it("rejects every other web origin", () => {
    expect(allowed("https://evil.example.com")).toBe(false);
    expect(allowed("http://attendance.example.com")).toBe(false);
    expect(allowed("https://attendance.example.com.evil.net")).toBe(false);
    expect(allowed("null")).toBe(false);
    expect(allowed("not a url")).toBe(false);
  });

  it("allows requests with no Origin header (Android app, curl, ESP32 nodes)", () => {
    expect(allowed(undefined)).toBe(true);
    expect(allowed("")).toBe(true);
  });

  it("supports wildcard sub-domains without matching look-alike hosts", () => {
    expect(allowed("https://smart-attendance.vercel.app")).toBe(true);
    expect(allowed("https://a.b.vercel.app")).toBe(false);
    expect(allowed("https://vercel.app.evil.net")).toBe(false);
  });

  it("treats * as allow-all (development only)", () => {
    const any = createOriginMatcher(["*"]);
    expect(any("https://anything.example.com")).toBe(true);
    expect(any(undefined)).toBe(true);
  });
});

describe("origin normalisation", () => {
  it("drops trailing slashes and lower-cases", () => {
    expect(normalizeOrigin("https://App.Example.com/")).toBe("https://app.example.com");
  });
  it("rejects paths and non-http schemes", () => {
    expect(normalizeOrigin("https://app.example.com/api")).toBe("https://app.example.com");
    expect(normalizeOrigin("ftp://app.example.com")).toBeNull();
    expect(normalizeOrigin("")).toBeNull();
  });
});

describe("proxy and CORS configuration", () => {
  const base = { DATABASE_URL: "postgres://x/y", JWT_SECRET: "s".repeat(40) };

  it("defaults to trusting one proxy hop and Cloudflare's header", () => {
    const cfg = loadConfig(base);
    expect(cfg.TRUST_PROXY_HOPS).toBe(1);
    expect(cfg.TRUST_CF_CONNECTING_IP).toBe(true);
    expect(cfg.RATE_LIMIT_MAX).toBe(300);
  });

  it("refuses an explicit list that is not needed in production and wildcards", () => {
    expect(() => loadConfig({ ...base, NODE_ENV: "production" })).toThrow(/CORS_ORIGINS/);
    expect(() => loadConfig({ ...base, NODE_ENV: "production", CORS_ORIGINS: "*" })).toThrow(/CORS_ORIGINS/);
  });

  it("rejects origins with a path or query string", () => {
    expect(() =>
      loadConfig({ ...base, NODE_ENV: "production", CORS_ORIGINS: "https://app.example.com/app" }),
    ).toThrow(/bare origins/);
    expect(() =>
      loadConfig({ ...base, NODE_ENV: "production", CORS_ORIGINS: "https://app.example.com?x=1" }),
    ).toThrow(/bare origins/);
  });

  it("accepts the real origin list", () => {
    const cfg = loadConfig({
      ...base,
      NODE_ENV: "production",
      CORS_ORIGINS: "https://attendance.example.com,https://lecturer.example.com",
    });
    expect(cfg.CORS_ORIGINS).toContain("attendance.example.com");
    expect(cfg.NODE_ENV).toBe("production");
  });
});
