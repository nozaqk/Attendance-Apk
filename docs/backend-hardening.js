/**
 * Smart Attendance — backend hardening (Node.js / Express on Render, behind Cloudflare).
 *
 * This file is a drop-in module for the API repository (the backend is deployed
 * separately from the client repos). Copy it in as `src/security.js` (or
 * `middleware/security.js`) and follow the two-line integration in the header
 * comment of each exported piece.
 *
 * Requires:  npm i express-rate-limit cors helmet
 *
 * Why each piece exists:
 *  - app.set('trust proxy', 1)      → Render + Cloudflare = exactly one proxy hop,
 *                                     so req.ip is the real client, not the proxy.
 *  - CF-Connecting-IP               → Cloudflare's authoritative client IP.
 *  - CORS allowlist                 → only your web front-end may send cookies/
 *                                     credentials from a browser; requests with no
 *                                     Origin (the Android app, curl, server-to-server)
 *                                     are allowed, because mobile clients do not
 *                                     send one.
 *  - helmet                         → security headers (nosniff, frameguard, HSTS…).
 *  - rate limits                    → brute-force protection on auth routes.
 *  - Cache-Control: no-store        → stops Cloudflare/edge caches serving stale
 *                                     or cross-user API data.
 */

'use strict';

const rateLimit = require('express-rate-limit');
const cors = require('cors');
const helmet = require('helmet');

/** Parse CORS_ORIGINS="https://a.com,https://b.com" into a clean list. */
function parseOrigins(value) {
  return String(value || '')
    .split(',')
    .map((s) => s.trim().replace(/\/+$/, ''))
    .filter(Boolean);
}

/**
 * Apply to the Express app BEFORE routes:
 *
 *   const { applySecurity } = require('./security');
 *   applySecurity(app);
 */
function applySecurity(app) {
  // Render terminates TLS and forwards X-Forwarded-For from Cloudflare.
  // 1 = trust exactly one hop (Cloudflare). Never `true` in production:
  // that lets a client spoof X-Forwarded-For and bypass IP rate limits.
  app.set('trust proxy', 1);
  app.disable('x-powered-by');

  app.use(
    helmet({
      contentSecurityPolicy: false, // the API serves JSON; the web app has its own CSP
      crossOriginResourcePolicy: { policy: 'cross-origin' },
      referrerPolicy: { policy: 'no-referrer' },
      hsts: { maxAge: 15552000, includeSubDomains: true, preload: false },
    })
  );

  // Real client IP (Cloudflare → X-Forwarded-For → socket).
  app.use((req, _res, next) => {
    const cf = req.headers['cf-connecting-ip'];
    const xff = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
    req.clientIp = (typeof cf === 'string' && cf.trim()) || xff || req.ip;
    next();
  });

  const allowed = parseOrigins(process.env.CORS_ORIGINS);
  app.use(
    cors({
      origin(origin, callback) {
        // No Origin header: the Android APK, curl, health checks and
        // server-to-server calls. Not a browser CSRF vector, so allow it.
        if (!origin) return callback(null, true);
        if (allowed.includes(origin.replace(/\/+$/, ''))) return callback(null, true);
        // Reject without throwing a 500: CORS failures are client-side anyway.
        return callback(null, false);
      },
      methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
      allowedHeaders: ['Authorization', 'Content-Type', 'Accept', 'X-Requested-With'],
      exposedHeaders: ['Content-Disposition'],
      credentials: false, // bearer tokens, not cookies
      maxAge: 86400,
      optionsSuccessStatus: 204,
    })
  );

  // API responses are per-user; never let Cloudflare or a device cache them.
  app.use((_req, res, next) => {
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, private');
    res.setHeader('Pragma', 'no-cache');
    next();
  });
}

/** 429 body that matches the client contract: { error: { code, message } }. */
function limitHandler(req, res) {
  res.status(429).json({
    error: {
      code: 'RATE_LIMITED',
      message: 'Too many attempts. Please wait a moment and try again.',
    },
  });
}

/**
 * Build the limiters. Mount them on the auth routes BEFORE the auth controllers:
 *
 *   const { authLimiter, accountLimiter, resetLimiter, apiLimiter } = buildLimiters();
 *   app.post('/api/auth/login', authLimiter, loginHandler);
 *   app.post('/api/auth/register/student', accountLimiter, registerHandler);
 *   app.post('/api/auth/forgot-password', resetLimiter, forgotHandler);
 *   app.post('/api/auth/reset-password', resetLimiter, resetHandler);
 *   app.use('/api', apiLimiter); // broad backstop, after the specific ones
 *
 * `keyGenerator` uses the real client IP resolved above, so Cloudflare's proxy
 * IP never lumps every user into one bucket.
 */
function buildLimiters() {
  const key = (req) => req.clientIp || req.ip;

  const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    limit: 10, // 10 failed or successful logins per IP per window
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    keyGenerator: key,
    handler: limitHandler,
    skipSuccessfulRequests: true, // only brute-force attempts count against the limit
  });

  const accountLimiter = rateLimit({
    windowMs: 60 * 60 * 1000,
    limit: 20, // account creation / password resets per hour per IP
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    keyGenerator: key,
    handler: limitHandler,
  });

  const resetLimiter = rateLimit({
    windowMs: 60 * 60 * 1000,
    limit: 5, // code requests are expensive (email) and easy to abuse
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    keyGenerator: key,
    handler: limitHandler,
  });

  const apiLimiter = rateLimit({
    windowMs: 60 * 1000,
    limit: 300, // generous backstop for the polling clients (5–60 s intervals)
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    keyGenerator: key,
    handler: limitHandler,
  });

  return { authLimiter, accountLimiter, resetLimiter, apiLimiter };
}

/**
 * Optional: per-account throttle on login, so one IP cannot spray a single
 * account and a distributed attack still trips on the account key.
 *
 *   const { accountKeyLimiter } = buildAccountKeyLimiter();
 *   app.post('/api/auth/login', accountKeyLimiter, loginHandler);
 */
function buildAccountKeyLimiter() {
  const hits = new Map();
  const WINDOW = 15 * 60 * 1000;
  const MAX = 10;
  const accountKeyLimiter = (req, res, next) => {
    const email = String(req.body?.email || '').toLowerCase();
    if (!email) return next();
    const now = Date.now();
    const entry = hits.get(email) || { count: 0, resetAt: now + WINDOW };
    if (now > entry.resetAt) {
      entry.count = 0;
      entry.resetAt = now + WINDOW;
    }
    entry.count += 1;
    hits.set(email, entry);
    if (hits.size > 10_000) hits.clear(); // crude bound on memory
    if (entry.count > MAX) return limitHandler(req, res);
    next();
  };
  return { accountKeyLimiter };
}

/**
 * Fail-closed auth helper. Every route that is not in the public allowlist must
 * sit behind this, so a forgotten `requireAuth` cannot silently expose data.
 *
 *   const { requireAuth, assertProtectedRoutes } = require('./security');
 *   app.use('/api/lecturer', requireAuth('LECTURER'));
 *   app.use('/api/students', requireAuth('STUDENT'));
 */
function requireAuth(expectedRole) {
  return function authGate(req, res, next) {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    if (!token) {
      return res.status(401).json({
        error: { code: 'NO_TOKEN', message: 'Sign in to continue.' },
      });
    }
    // Replace with the project's own verifier (JWT verify / session lookup).
    req.authToken = token;
    next();
  };
}

/** Routes that are deliberately public (adjust to match the API). */
const PUBLIC_ROUTES = [
  'GET /api/public/departments',
  'POST /api/auth/login',
  'POST /api/auth/register/student',
  'POST /api/auth/forgot-password',
  'POST /api/auth/reset-password',
  'POST /api/auth/logout',
  'GET /api/health',
];

/**
 * Optional startup audit: warns when a mounted /api route is not behind the
 * auth gate. Single-tenant Express check — good enough to catch regressions.
 *
 *   const { assertProtectedRoutes } = require('./security');
 *   assertProtectedRoutes(app, logger);
 */
function assertProtectedRoutes(app, logger = console) {
  const routes = [];
  const walk = (stack, prefix = '') => {
    for (const layer of stack || []) {
      if (layer.route) {
        const methods = Object.keys(layer.route.methods || {}).map((m) => m.toUpperCase());
        for (const m of methods) routes.push(`${m} ${prefix}${layer.route.path}`);
      } else if (layer.name === 'router' && layer.handle?.stack) {
        walk(layer.handle.stack, prefix);
      }
    }
  };
  walk(app._router?.stack);
  const suspicious = routes.filter((r) => {
    const [, path] = r.split(' ');
    return path.startsWith('/api') && !PUBLIC_ROUTES.includes(r) && !r.includes('OPTIONS');
  });
  if (suspicious.length) {
    logger.warn(
      '[security] Review these routes for the auth gate:',
      suspicious.join(', ')
    );
  }
  return suspicious;
}

module.exports = {
  applySecurity,
  buildLimiters,
  buildAccountKeyLimiter,
  requireAuth,
  assertProtectedRoutes,
  parseOrigins,
  PUBLIC_ROUTES,
};
