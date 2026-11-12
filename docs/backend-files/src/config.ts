import { z } from "zod";

const bool = z
  .enum(["true", "false"])
  .default("true")
  .transform((v) => v === "true");

const schema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().int().default(3000),
  HOST: z.string().default("0.0.0.0"),

  DATABASE_URL: z.string().min(1, "DATABASE_URL is required"),
  JWT_SECRET: z.string().min(32, "JWT_SECRET must be at least 32 characters"),
  JWT_EXPIRES_IN: z.string().default("12h"),
  /** Comma separated list of allowed browser origins, or "*" (development only). */
  CORS_ORIGINS: z.string().default(""),
  /**
   * How many proxy hops sit between the internet and this process.
   * Render's edge is one hop; keep 1 unless you add another proxy in front of Render.
   * 0 disables X-Forwarded-For parsing entirely.
   */
  TRUST_PROXY_HOPS: z.coerce.number().int().min(0).max(5).default(1),
  /** Cloudflare puts the real client IP in CF-Connecting-IP. Turn off if you are not behind Cloudflare. */
  TRUST_CF_CONNECTING_IP: bool,
  /** Requests allowed per minute per client IP for the whole API. */
  RATE_LIMIT_MAX: z.coerce.number().int().positive().default(300),

  /** Local time zone as minutes east of UTC (Ghana = 0). Used to turn "HH:mm" schedules into instants. */
  TZ_OFFSET_MINUTES: z.coerce.number().int().default(0),
  BCRYPT_ROUNDS: z.coerce.number().int().min(4).max(15).default(12),

  /** Attendance rules */
  EDIT_WINDOW_HOURS: z.coerce.number().positive().default(6),
  LATE_AFTER_MINUTES: z.coerce.number().int().min(0).default(15),
  /** A selected-but-not-started class releases its room after this long. */
  PENDING_CLASS_TTL_MINUTES: z.coerce.number().int().min(1).default(15),
  /** A class the lecturer forgot to close is closed automatically after this many hours. */
  MAX_CLASS_HOURS: z.coerce.number().positive().default(6),
  OBSERVATION_MAX_SKEW_SECONDS: z.coerce.number().int().min(5).default(120),
  OBSERVATION_MIN_INTERVAL_SECONDS: z.coerce.number().int().min(0).default(30),
  MIN_OBSERVATIONS: z.coerce.number().int().min(1).default(2),
  MIN_ACCEPT_RSSI: z.coerce.number().int().max(0).default(-95),
  REQUIRE_LECTURER_BLE: bool,
  LECTURER_VERIFY_TTL_SECONDS: z.coerce.number().int().min(10).default(300),
  NODE_OFFLINE_AFTER_SECONDS: z.coerce.number().int().min(10).default(120),
  /** Shared key that lets a NEW ESP32 register itself on its first heartbeat. Empty = auto-registration off. */
  NODE_ENROLL_KEY: z.string().default(""),

  /** Password reset e-mail (HTTPS API, works on Render free tier where SMTP ports are blocked). */
  /** Brevo (https://brevo.com) is used when BREVO_API_KEY is set; otherwise Resend when RESEND_API_KEY is set. */
  BREVO_API_KEY: z.string().default(""),
  RESEND_API_KEY: z.string().default(""),
  /** The verified sender address (Brevo: Senders & IP > Senders). */
  EMAIL_FROM: z.string().default(""),
  EMAIL_FROM_NAME: z.string().default("Smart Attendance"),
  PASSWORD_RESET_TTL_MINUTES: z.coerce.number().int().min(5).default(30),
  /** Public URLs of the four frontends; used to build the reset-password link per role. */
  FRONTEND_URL_ADMIN: z.string().default(""),
  FRONTEND_URL_QA: z.string().default(""),
  FRONTEND_URL_LECTURER: z.string().default(""),
  FRONTEND_URL_STUDENT: z.string().default(""),

  /** Only the demo seed script reads this. Never set in production. */
  ALLOW_DEMO_SEED: z.enum(["true", "false"]).default("false"),
});

export type Config = z.infer<typeof schema>;

export function loadConfig(env: Record<string, string | undefined> = process.env): Config {
  const parsed = schema.safeParse(env);
  if (!parsed.success) {
    const msg = parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
    throw new Error(`Invalid environment configuration: ${msg}`);
  }
  const cfg = parsed.data;
  if (cfg.NODE_ENV === "production" && (cfg.CORS_ORIGINS.trim() === "" || cfg.CORS_ORIGINS.trim() === "*")) {
    throw new Error("CORS_ORIGINS must list the frontend origins explicitly in production");
  }
  const badOrigins: string[] = [];
  for (const entry of cfg.CORS_ORIGINS.split(",").map((s) => s.trim()).filter(Boolean)) {
    if (entry === "*") continue;
    const bare = entry.replace(/\*/, "wildcard");
    let ok = false;
    try {
      const url = new URL(bare);
      ok = (url.protocol === "http:" || url.protocol === "https:") && url.pathname === "/" && !url.search && !url.hash;
    } catch {
      ok = false;
    }
    if (!ok) badOrigins.push(entry);
  }
  if (badOrigins.length > 0) {
    throw new Error(
      `CORS_ORIGINS entries must be bare origins like https://attendance.example.com (no path, query or trailing slash): ${badOrigins.join(", ")}`,
    );
  }
  if (cfg.NODE_ENROLL_KEY && cfg.NODE_ENROLL_KEY.length < 16) {
    throw new Error("NODE_ENROLL_KEY must be at least 16 characters (or leave it empty to turn auto-registration off)");
  }
  return cfg;
}

export function parseCorsOrigins(value: string): string[] | true {
  const v = value.trim();
  if (v === "*" || v === "") return true;
  return v.split(",").map((s) => s.trim().replace(/\/$/, "")).filter(Boolean);
}
