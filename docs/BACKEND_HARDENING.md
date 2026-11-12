# Backend hardening: Cloudflare domain, CORS, real client IP, rate limits

The API is Fastify 5 + Prisma on Render. This document is the backend half of the
custom-domain work described in `DOMAIN_AND_API_SECURITY.md`: what the audit found,
what changed, how to apply it, and the environment checklist.

- Patch: [`backend-hardening.patch`](./backend-hardening.patch)
- Full files (fallback): [`backend-files/`](./backend-files/)

Everything below is verified: `npx tsc -p tsconfig.json --noEmit` is clean for the
changed files and `npx vitest run tests/unit` passes 43/43 (19 new network tests,
5 new HTTP-wiring tests). Integration tests need `TEST_DATABASE_URL` and were not
run here.

---

## 1. What the audit found

| Area | Before | Risk |
| --- | --- | --- |
| `trustProxy` | `true` | A client could send its own `X-Forwarded-For` and get a fresh rate-limit bucket / fake IP in logs. |
| Client IP | `req.ip` only | Behind Cloudflare + Render that is the proxy's address, so limits were shared or meaningless and `CF-Connecting-IP` was ignored. |
| CORS | `origin: true` when `CORS_ORIGINS` is `*`/empty, no `credentials`/`maxAge`, no `PATCH`, thin header allow-list | Any site could call the API from a browser in misconfigured deployments; preflights re-issued on every request. |
| Rate limits | One global limiter (300/min). Login/register/forgot/reset had a single 10/min `strict` config | Password guessing and reset-code farming were barely throttled and shared the global bucket. |
| 429 body | Fastify default | Did not match `{ error: { code, message } }`, so the web/Android clients showed a generic failure. |
| Caching | No cache headers | Cloudflare or a browser could serve one user's JSON to another. |
| Auth | `authenticate` + `requireRole` per plugin | Correct — but undocumented as a contract. It is now audited and written down. |
| Env validation | `CORS_ORIGINS` non-empty in prod | An entry like `https://site.com/` or `https://site.com/app` would silently never match an `Origin`. |

## 2. Files changed

| File | Change |
| --- | --- |
| `src/utils/network.ts` **(new)** | `clientIpOf()` (CF-Connecting-IP → True-Client-IP → right-most `X-Forwarded-For` → `X-Real-IP` → socket), `isIpAddress()`, `normalizeOrigin()`, `createOriginMatcher()` (exact origins + `https://*.example.com` wildcards, allows Origin-less clients, rejects `null` origins). |
| `src/app.ts` | `trustProxy` is now a hop predicate (`hop < TRUST_PROXY_HOPS`, default 1) instead of `true`; `onRequest` hook sets `req.clientIp`; `onSend` hook sets `Cache-Control: no-store` on `/api*` and `/health`; CORS delegate + `credentials:false` + `maxAge` + `PATCH` + full header allow-list; global limiter keyed by `req.clientIp` with `RATE_LIMIT_MAX`; 429 mapped to the shared error envelope; startup log of the effective config. |
| `src/routes/auth.ts` | Per-route budgets keyed by `req.clientIp`: login 10/15 min, register 20/hour, forgot-password 5/15 min, reset-password 10/15 min, change-password 10/15 min (tests keep the high ceiling). |
| `src/config.ts` | New `TRUST_PROXY_HOPS` (0–5, default 1), `TRUST_CF_CONNECTING_IP` (default true), `RATE_LIMIT_MAX` (default 300); `CORS_ORIGINS` entries are validated as bare `http(s)` origins with no path/query. |
| `src/types.d.ts` | `FastifyRequest.clientIp: string`. |
| `tests/unit/network.test.ts` **(new)** | 19 tests: IP parsing, spoofed/malformed proxy headers, right-most XFF, wildcard CORS, `null` origin, `CORS_ORIGINS` validation. |
| `tests/unit/httpSecurity.test.ts` **(new)** | 5 tests against a real `buildApp()` (stub Prisma, no DB): `no-store` header, allowed origin gets `Access-Control-Allow-Origin` and a foreign origin does not, Origin-less request served, 429 envelope, per-IP buckets (a spoofed XFF does not reset the bucket). |
| `.env.example` | Documents the three new variables and the exact `CORS_ORIGINS` format. |
| `README.md` | New "Custom domain, proxies, CORS and rate limits" section (limits table, 429 body, public-route list). |

## 3. Applying it

```bash
cd Attendance-Backend            # the backend repo
git switch -c hardening/cloudflare-domain
git apply --check /path/to/docs/backend-hardening.patch   # verify, then:
git apply /path/to/docs/backend-hardening.patch

npm ci
npx prisma generate            # the client is generated, not committed
npm run typecheck
npx vitest run tests/unit      # 43 tests, no database needed
```

If `git apply` complains because your copy has drifted, the same files are in
`docs/backend-files/` with their original paths — copy them over
(`src/`, `tests/unit/`, `.env.example`, `README.md`).

Then set the environment (section 4), deploy, and re-run the checks in section 6.

## 4. Environment checklist

### Render → API service → Environment

| Variable | Value | Notes |
| --- | --- | --- |
| `CORS_ORIGINS` | `https://student.yourdomain.com,https://lecturer.yourdomain.com,https://qa.yourdomain.com,https://admin.yourdomain.com` | Bare origins, comma separated, no trailing slash, no path. **Required** in production; `*` is refused. Add the Vercel preview domain only temporarily. |
| `TRUST_PROXY_HOPS` | `1` | Render's edge. Raise only if you add another proxy in front of Render. |
| `TRUST_CF_CONNECTING_IP` | `true` | Keep `true` while the service is only reachable through Cloudflare. |
| `RATE_LIMIT_MAX` | `300` | Requests/minute/IP for the API as a whole; auth routes have their own budgets. |
| `NODE_ENV` | `production` | Enables the strict `CORS_ORIGINS` checks. |
| `DATABASE_URL`, `DIRECT_URL`, `JWT_SECRET`, `BREVO_API_KEY`/`RESEND_API_KEY`, `EMAIL_FROM`, `FRONTEND_URL_*` | unchanged | Secrets stay here only. |

Nothing above is secret except the values you already had.

### Local `.env` (backend)

```bash
NODE_ENV=development
CORS_ORIGINS=http://localhost:5173,http://localhost:5174
TRUST_PROXY_HOPS=1
TRUST_CF_CONNECTING_IP=false   # you are calling the origin directly
RATE_LIMIT_MAX=300
```

### Frontend / APK (already done in this repo)

```bash
# web (this repo, both apps) and Vercel project settings
VITE_API_URL=https://api.yourdomain.com
# GitHub Actions → Variables, for the APK build
API_BASE_URL=https://api.yourdomain.com
```

## 5. Cloudflare side (short version)

1. `api.yourdomain.com` → proxied CNAME to `<service>.onrender.com`; SSL/TLS
   **Full (strict)**.
2. Cache rule: bypass cache for `/api*` (belt and braces on top of `no-store`).
3. Optional WAF rate-limit rule on `/api/auth/*` (e.g. 20/min/IP).
4. Restrict Render to Cloudflare traffic (IP ranges or Authenticated Origin Pulls)
   so nobody can forge `CF-Connecting-IP` by calling Render directly.

## 6. Verification

```bash
# 1. no-store on API responses
curl -sS -D- -o /dev/null https://api.yourdomain.com/api/public/departments | grep -i cache-control

# 2. preflight from an allowed origin (expect the ACAO header)
curl -sS -i -X OPTIONS https://api.yourdomain.com/api/auth/login \
  -H 'Origin: https://student.yourdomain.com' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,authorization'

# 3. preflight from a foreign origin (expect NO Access-Control-Allow-Origin)
curl -sS -i -X OPTIONS https://api.yourdomain.com/api/auth/login \
  -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: POST'

# 4. Android-style request with no Origin still works
curl -sS -o /dev/null -w '%{http_code}\n' https://api.yourdomain.com/api/public/departments

# 5. login brute force → 429 with the shared envelope
for i in $(seq 1 12); do
  curl -sS -o /dev/null -w '%{http_code} ' -X POST https://api.yourdomain.com/api/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"nobody@example.com","password":"nope"}'
done; echo
curl -sS -X POST https://api.yourdomain.com/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"nobody@example.com","password":"nope"}' | head -c 200

# 6. protected routes without a token → 401
curl -sS -o /dev/null -w '%{http_code}\n' https://api.yourdomain.com/api/student/profile
curl -sS -o /dev/null -w '%{http_code}\n' https://api.yourdomain.com/api/lecturer/sessions
curl -sS -o /dev/null -w '%{http_code}\n' https://api.yourdomain.com/api/admin/stats
```

The Render log line `http security configuration` shows the values the process
actually booted with (`trustProxy`, `trustCfConnectingIp`, `corsOrigins`,
`rateLimitPerMinute`) — check it after every deploy.

## 7. When the domain is decided

The clients already read a single variable, so switching over is one line each:

```bash
# .env.production (root and student-frontend/) — delete the legacy line, then:
VITE_API_URL=https://api.yourdomain.com
# android, no rebuild config needed: API_BASE_URL=/path/to/scripts/build-apk.sh or the CI variable
```

Redeploy both web apps (Vite inlines the value at build time) and rebuild the APK.
Nothing in the code changes.
