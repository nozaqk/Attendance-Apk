import Fastify, { type FastifyInstance } from "fastify";
import cors from "@fastify/cors";
import helmet from "@fastify/helmet";
import jwt from "@fastify/jwt";
import rateLimit from "@fastify/rate-limit";
import { ZodError } from "zod";
import { loadConfig, parseCorsOrigins, type Config } from "./config.js";
import { createPrisma, type Db } from "./db.js";
import { AppError } from "./utils/errors.js";
import { clientIpOf, createOriginMatcher } from "./utils/network.js";
import { authRoutes } from "./routes/auth.js";
import { studentRoutes } from "./routes/student.js";
import { lecturerRoutes } from "./routes/lecturer.js";
import { bleRoutes } from "./routes/ble.js";
import { nodeRoutes } from "./routes/nodes.js";
import { adminRoutes } from "./routes/admin.js";
import { academicRoutes } from "./routes/academic.js";
import { reportRoutes } from "./routes/reports.js";
import { qaRoutes } from "./routes/qa.js";
import { infrastructureRoutes } from "./routes/infrastructure.js";
import { publicRoutes } from "./routes/public.js";
import { createMailer, type Mailer } from "./services/mailer.js";

export interface BuildOptions {
  config?: Config;
  prisma?: Db;
  logger?: boolean;
  mailer?: Mailer;
}

export async function buildApp(opts: BuildOptions = {}): Promise<FastifyInstance> {
  const config = opts.config ?? loadConfig();
  const prisma = opts.prisma ?? createPrisma(config.DATABASE_URL);

  const app = Fastify({
    logger: opts.logger ?? config.NODE_ENV !== "test",
    // Render terminates TLS one hop in front of us; Cloudflare sits in front of Render.
    // Trusting exactly the configured number of hops keeps forged X-Forwarded-For headers
    // from being able to pick their own client IP (and therefore their own rate-limit bucket).
    // Fastify's typings only accept a hop predicate (a raw number works at runtime but not in
    // the type definitions), so express "trust N hops" as "trust while hop < N".
    trustProxy:
      config.TRUST_PROXY_HOPS === 0
        ? false
        : (_address: string, hop: number) => hop < config.TRUST_PROXY_HOPS,
    bodyLimit: 1_000_000,
  });

  app.decorate("prisma", prisma);
  app.decorate("appConfig", config);
  app.decorate("mailer", opts.mailer ?? createMailer(config));
  app.decorateRequest("auth", null as never);
  app.decorateRequest("clientIp", "");

  // Resolve the real client address once per request: rate limiting, activity logs and the
  // audit trail all read `req.clientIp` instead of the proxy's socket address.
  app.addHook("onRequest", async (req) => {
    req.clientIp = clientIpOf(req.headers, req.socket.remoteAddress ?? req.ip, {
      trustCfConnectingIp: config.TRUST_CF_CONNECTING_IP,
    });
  });

  // API responses are per-user and authenticated: never let a proxy or browser cache them.
  app.addHook("onSend", async (req, reply, payload) => {
    if (req.url.startsWith("/api") || req.url === "/health") {
      reply.header("cache-control", "no-store");
      reply.header("pragma", "no-cache");
    }
    return payload;
  });

  await app.register(helmet);

  const corsOrigins = parseCorsOrigins(config.CORS_ORIGINS);
  const originMatcher = createOriginMatcher(Array.isArray(corsOrigins) ? corsOrigins : ["*"]);
  await app.register(cors, {
    // Requests without an Origin header (the Android app, curl, the ESP32 nodes) are allowed;
    // browsers always send one, so only the listed web origins get an Access-Control-Allow-Origin
    // header and anything else is blocked by the browser.
    origin: (origin, cb) => cb(null, originMatcher(origin)),
    credentials: false,
    methods: ["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allowedHeaders: [
      "Content-Type",
      "Authorization",
      "Accept",
      "Accept-Language",
      "X-Requested-With",
      "X-Enroll-Key",
    ],
    exposedHeaders: ["Content-Disposition", "Retry-After"],
    maxAge: 86_400,
    strictPreflight: true,
  });

  await app.register(rateLimit, {
    global: true,
    max: config.NODE_ENV === "test" ? 10_000 : config.RATE_LIMIT_MAX,
    timeWindow: "1 minute",
    // Bucket per real client IP, not per proxy.
    keyGenerator: (req) => req.clientIp || req.ip,
    // The plugin *throws* whatever this returns, so hand it a real AppError: our error handler
    // then formats it as the usual `{ error: { code, message } }` envelope with a 429 status.
    errorResponseBuilder: (_req, context) =>
      new AppError(429, "RATE_LIMITED", `Too many requests. Try again in ${context.after}.`, {
        max: context.max,
        retryAfter: context.after,
      }),
  });
  await app.register(jwt, {
    secret: config.JWT_SECRET,
    sign: { expiresIn: config.JWT_EXPIRES_IN },
  });

  app.setErrorHandler((rawErr: unknown, req, reply) => {
    const err = rawErr as Error;
    if (err instanceof AppError) {
      return reply.status(err.statusCode).send({ error: { code: err.code, message: err.message, details: err.details } });
    }
    if (err instanceof ZodError) {
      return reply.status(400).send({ error: { code: "VALIDATION_ERROR", message: "Invalid request data" } });
    }
    const status = (err as { statusCode?: number }).statusCode;
    if (status && status >= 400 && status < 500) {
      return reply.status(status).send({ error: { code: (err as { code?: string }).code ?? "BAD_REQUEST", message: err.message } });
    }
    req.log.error(err);
    return reply.status(500).send({ error: { code: "INTERNAL_ERROR", message: "Something went wrong" } });
  });
  app.setNotFoundHandler((_req, reply) =>
    reply.status(404).send({ error: { code: "NOT_FOUND", message: "Route not found" } }),
  );

  app.get("/health", async () => ({ status: "ok", time: new Date().toISOString() }));

  await app.register(authRoutes, { prefix: "/api/auth" });
  await app.register(studentRoutes, { prefix: "/api/student" });
  await app.register(lecturerRoutes, { prefix: "/api/lecturer" });
  await app.register(bleRoutes, { prefix: "/api/attendance" });
  await app.register(nodeRoutes, { prefix: "/api/nodes" });
  await app.register(publicRoutes, { prefix: "/api/public" });
  // Admin: staff, students, activity. QA: overview, class records.
  await app.register(adminRoutes, { prefix: "/api/admin" });
  await app.register(qaRoutes, { prefix: "/api/qa" });
  // Rooms and ESP32 nodes are ADMIN-only.
  await app.register(infrastructureRoutes, { prefix: "/api/admin" });
  // Academic structure + reports are used by the QA dashboard; the admin prefix keeps
  // working too so both roles can use the same API.
  for (const prefix of ["/api/qa", "/api/admin"]) {
    await app.register(academicRoutes, { prefix });
    await app.register(reportRoutes, { prefix });
  }

  app.addHook("onClose", async () => {
    if (!opts.prisma) await prisma.$disconnect();
  });

  app.log.info(
    {
      trustProxy: config.TRUST_PROXY_HOPS,
      trustCfConnectingIp: config.TRUST_CF_CONNECTING_IP,
      corsOrigins: Array.isArray(corsOrigins) ? corsOrigins : "*",
      rateLimitPerMinute: config.RATE_LIMIT_MAX,
    },
    "http security configuration",
  );
  return app;
}
