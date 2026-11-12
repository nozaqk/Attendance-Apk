# Custom domain + API security migration

Goal: every client (lecturer web app, student web app, native Android APK) talks
to **`https://yourdomain.com`** (Cloudflare → Render) instead of the
`*.onrender.com` host, and the API is hardened for public exposure.

Replace `yourdomain.com` with your real domain everywhere it appears below
(`.env.production`, the Render/GitHub variables). The build logs an error if a
build still ships the placeholder.

---

## 1. Client configuration (this repository)

The backend host now lives in **one variable per platform**. No component,
screen or data class contains a host name.

### Web (both Vite apps)

| What | Where |
| --- | --- |
| Single source of truth | `VITE_API_URL` (build-time env var) |
| Read in code | `src/shared/api.ts` and `student-frontend/src/shared/api.ts` → `export const API_BASE_URL` |
| Example file | `.env.example` (root and `student-frontend/`) |
| Deployment values | `.env.production` (root and `student-frontend/`) |

```bash
# .env.production
VITE_API_URL=https://yourdomain.com
```

Notes

- `VITE_API_BASE_URL` is still honoured as a legacy alias, so nothing breaks
  during the switch; `VITE_API_URL` wins when both are set.
- Trailing slashes are stripped; an empty value means "same origin" (handy when
  a dev server proxies `/api`).
- Vite bakes `VITE_*` values into the bundle at build time — these are **public**
  by definition. Never put secrets in them. The values are inlined into
  `dist/assets/*.js`, so **rebuild after changing the domain**.
- Vite inlines *every* `VITE_*` variable, so remove the old
  `VITE_API_BASE_URL=…onrender.com` line from `.env.production` once you have
  switched, otherwise the old host string still ships in the bundle (harmless,
  but pointless).

### Native Android app

| What | Where |
| --- | --- |
| Build input | Gradle property `API_BASE_URL` or env var `API_BASE_URL` |
| Generated constant | `BuildConfig.API_BASE_URL` (`android/app/build.gradle.kts`) |
| Read in code | `data/Api.kt` → `ApiClient.BASE` |
| Default | `https://attendance-system-33pl.onrender.com` (only when nothing is set) |

```bash
# local build
API_BASE_URL=https://yourdomain.com ./scripts/build-apk.sh
# or
cd android && ./gradlew assembleDebug -PAPI_BASE_URL=https://yourdomain.com
```

CI (`.github/workflows/build-apk.yml`) reads the repository **variable**
`API_BASE_URL` (Settings → Secrets and variables → Actions → **Variables**) and
falls back to the Render host when it is unset. The build step prints the host it
is using, and the app logs an error at startup if it was built with the
placeholder domain.

---

## 2. Files modified

| File | Change |
| --- | --- |
| `src/shared/api.ts` | `VITE_API_URL` (preferred) + `VITE_API_BASE_URL` alias, exports `API_BASE_URL`, strips trailing `/`, placeholder-domain guard, error text updated |
| `student-frontend/src/shared/api.ts` | same change (the two apps keep separate copies) |
| `.env.example` (new) | documented `VITE_API_URL`, fallback host, legacy var |
| `student-frontend/.env.example` (new) | same |
| `.gitignore` | ignores `.env.local` / `.env.*.local` |
| `android/app/build.gradle.kts` | `apiBaseUrl` resolution + `buildConfigField("String","API_BASE_URL", …)`, `buildConfig = true` |
| `android/app/src/main/java/com/smartattendance/app/data/Api.kt` | `ApiClient.BASE` now reads `BuildConfig.API_BASE_URL` (no hardcoded host) |
| `android/app/src/main/java/com/smartattendance/app/MainActivity.kt` | startup `Log.e` if built with the placeholder domain |
| `.github/workflows/build-apk.yml` | passes `API_BASE_URL` from repo variable to Gradle |
| `scripts/build-apk.sh` | honours `API_BASE_URL` |
| `docs/BACKEND_HARDENING.md` (new) | backend audit + the exact Fastify changes, with the env checklist and verification steps |
| `docs/backend-hardening.patch` (new) | unified diff for the Fastify API (`git apply` inside the backend repo) |
| `docs/backend-files/` (new) | the same modified/new backend files, for copy-paste if the patch does not apply |
| `docs/DOMAIN_AND_API_SECURITY.md` (new) | this document |
| `ANDROID_BUILD.md` | points at the new configuration |

The backend (Fastify 5 + Prisma) is not part of this repository, so the backend
changes ship as `docs/backend-hardening.patch` plus `docs/backend-files/`.
Section 3 summarises them; `docs/BACKEND_HARDENING.md` has the file-by-file
detail and the exact `git apply` / `npm test` commands.

---

## 3. Backend changes (Render API)

### 3.1 Real client IP behind Cloudflare and Render

`src/app.ts` used `trustProxy: true`, which lets a client pick its own entry in
`X-Forwarded-For` and therefore its own rate-limit bucket. It now trusts exactly
the configured number of hops:

```ts
trustProxy: (address, hop) => hop < config.TRUST_PROXY_HOPS, // default 1
```

On top of that, `src/utils/network.ts` resolves the address once per request and
stores it on `req.clientIp`:

1. `CF-Connecting-IP` (Cloudflare always overwrites it on proxied requests)
2. `True-Client-IP` (Cloudflare alias)
3. the **right-most** valid `X-Forwarded-For` entry — proxies append, so a client
   cannot forge what comes after its own address
4. `X-Real-IP`, then the socket address

Rate limiting, activity logs and the audit trail all read `req.clientIp`, so the
limits follow the browser/app rather than Cloudflare's edge.

> The service must stay reachable **only** through Cloudflare, otherwise a
> caller can send its own `CF-Connecting-IP`. Set `TRUST_CF_CONNECTING_IP=false`
> if you also call Render directly (e.g. during testing).

### 3.2 CORS: web origins only, but Origin-less clients still work

`@fastify/cors` is registered with a delegate instead of a blanket value:

```ts
origin: (origin, cb) => cb(null, originMatcher(origin)),
credentials: false,                    // bearer tokens, not cookies
methods: ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
allowedHeaders: ['Content-Type', 'Authorization', 'Accept', 'Accept-Language',
                 'X-Requested-With', 'X-Enroll-Key'],
exposedHeaders: ['Content-Disposition', 'Retry-After'],
maxAge: 86400,
```

- `originMatcher` accepts the entries of `CORS_ORIGINS` (exact origins and
  `https://*.example.com` wildcards), normalising case and trailing slashes.
- A request **without** an `Origin` header (the Android app, curl, the ESP32
  nodes) is allowed — that is not a CSRF hole because the API uses bearer
  tokens, never ambient cookies.
- Any other browser origin gets **no** `Access-Control-Allow-Origin` header, so
  the browser blocks the response while the server still answers normally.
- `CORS_ORIGINS="*"` is still allowed in development but `loadConfig` refuses it
  in production, and entries with a path or query string are rejected.

### 3.3 Rate limiting on the public/auth routes

The global limiter stays at `RATE_LIMIT_MAX` (default 300/min) but is now keyed
by `req.clientIp`, and each auth route carries its own budget:

| Route | Limit |
| --- | --- |
| `POST /api/auth/login` | 10 / 15 min |
| `POST /api/auth/register/student` | 20 / hour |
| `POST /api/auth/forgot-password` | 5 / 15 min |
| `POST /api/auth/reset-password` | 10 / 15 min (+ 5 wrong-code tries per account) |
| `POST /api/auth/change-password` | 10 / 15 min |

A rejected request returns HTTP 429 in the shared envelope (the same one the web
and Android clients already parse):

```json
{ "error": { "code": "RATE_LIMITED", "message": "Too many requests. Try again in 15 minutes.", "details": { "max": 10, "retryAfter": "15 minutes" } } }
```

Cloudflare rate-limiting rules (Security → WAF → Rate limiting rules) remain a
useful second layer on `/api/auth/*`; they use `CF-Connecting-IP` natively.

### 3.4 Authentication coverage

Audited every route in `src/routes/*.ts`: the only unauthenticated endpoints are
`/api/auth/login`, `/api/auth/register/student`, `/api/auth/forgot-password`,
`/api/auth/reset-password`, `/api/public/departments`, `GET /health` and
`POST /api/nodes/heartbeat` (which requires the ESP32 node key or the enrolment
key). Everything else is mounted behind `authenticate` / `requireRole(...)`:

- `/api/student/*`, `/api/attendance/ble-observation` → `STUDENT`
- `/api/lecturer/*` → `LECTURER`
- `/api/admin/*` → `ADMIN` (plus `ADMIN`-only rooms/nodes)
- `/api/qa/*` → `QA` (academic and report routes are also mounted under
  `/api/admin` for admins)

Tokens are revoked server-side on logout (`revokedToken.jti`), inactive accounts
are rejected, and scope tokens are refused.

### 3.5 Response/security headers

- `helmet` stays on (`X-Content-Type-Options`, `Referrer-Policy`, HSTS, …).
- A new `onSend` hook sets `Cache-Control: no-store` and `Pragma: no-cache` on
  every `/api` and `/health` response, so neither Cloudflare nor a browser can
  replay one user's JSON.
- `Access-Control-Expose-Headers` still exposes `Content-Disposition` for CSV
  exports (and now `Retry-After`).
- The effective proxy/CORS/rate-limit configuration is logged at startup
  (`http security configuration`).

### 3.6 Cloudflare settings

1. DNS: `yourdomain.com` (and `www`) proxied (orange cloud) → CNAME
   `<service>.onrender.com`.
2. SSL/TLS: **Full (strict)** — Render serves a valid certificate.
3. Edge Certificates: **Always Use HTTPS** on, HSTS on (start with 6 months).
4. Cache Rules: **Bypass cache** for `URI Path starts with /api` (or rely on
   `Cache-Control: no-store`, but belt and braces).
5. Optional but recommended: WAF rate-limiting rule on `/api/auth/*`
   (e.g. 20 requests / minute / IP), and a managed WAF ruleset.
6. Optional hardening: restrict the Render service to Cloudflare IP ranges, or
   enable Authenticated Origin Pulls, so the origin is not reachable directly.

---

## 4. Environment variables checklist

### Render (API service)

| Variable | Example | Notes |
| --- | --- | --- |
| `CORS_ORIGINS` | `https://yourdomain.com,https://www.yourdomain.com` | comma-separated, no trailing slash |
| `NODE_ENV` | `production` | enables strict behaviour |
| `TRUST_PROXY_HOPS` | `1` | proxies in front of Render; `0` ignores forwarded headers |
| `TRUST_CF_CONNECTING_IP` | `true` | use Cloudflare's `CF-Connecting-IP` as the client address |
| `RATE_LIMIT_MAX` | `300` | requests/minute/IP for the whole API (auth routes have their own) |

Keep database URLs, JWT secrets and mail credentials **only** here — never in a
client app.

### Local `.env` / `.env.local` (this repo, both apps)

```bash
VITE_API_URL=https://yourdomain.com     # or the Render host for debugging
```

### Vercel / hosting (both web deployments)

```bash
VITE_API_URL=https://yourdomain.com
```

Rebuild after changing it (the value is compiled into the bundle).

### GitHub Actions (APK build)

Settings → Secrets and variables → Actions → **Variables**:

```bash
API_BASE_URL=https://yourdomain.com
```

Nothing secret belongs in this variable — it is visible in the APK.

---

## 5. Verification

```bash
# 1. API reachable through the custom domain, no redirect loop
curl -sS -i https://yourdomain.com/api/public/departments | head -20

# 2. Preflight from the allowed web origin
curl -sS -i -X OPTIONS https://yourdomain.com/api/auth/login \
  -H 'Origin: https://yourdomain.com' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,authorization'

# 3. Preflight from a foreign origin must NOT be allowed
curl -sS -i -X OPTIONS https://yourdomain.com/api/auth/login \
  -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: POST'
#    → no Access-Control-Allow-Origin in the response

# 4. Protected endpoint without a token
curl -sS -i https://yourdomain.com/api/lecturer/sessions        # → 401
curl -sS -i https://yourdomain.com/api/student/profile          # → 401

# 5. Brute-force protection (expect 429 after the limit)
for i in $(seq 1 15); do
  curl -sS -o /dev/null -w "%{http_code} " -X POST \
    https://yourdomain.com/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"email":"nobody@example.com","password":"wrong-password"}'
done; echo

# 6. Real IP is logged/limited per client, not per proxy (429 once the bucket is empty)
curl -sS -i https://yourdomain.com/health -H 'CF-Connecting-IP: 203.0.113.7'

# 7. Responses must not be cacheable
curl -sS -D- -o /dev/null https://yourdomain.com/api/public/departments | grep -i cache-control
```

Client checks

1. Build with `VITE_API_URL=https://yourdomain.com npm run build` and confirm
   the bundle contains only the new host:
   `grep -o 'https://[a-z0-9.-]*' dist/assets/*.js | sort -u`.
2. Build the APK with `API_BASE_URL=https://yourdomain.com`; at runtime the app
   must call the custom domain (check `adb logcat` / any proxy).
3. Sign in as lecturer and student against the custom domain, accept the
   Bluetooth permission prompt, and confirm a class starts and notifications
   arrive (background alerts use the same base URL).

---

## 6. Security review of the client code (this repo)

- **No secrets in the clients.** Swept both web apps and the Android sources for
  API keys, private keys, database URLs, JWT secrets and long token literals:
  none. The only configuration is the public API host.
- **Tokens**: web stores the bearer token in `localStorage`
  (`attendance.token`); the APK stores it in per-role private
  `SharedPreferences`. Both are readable by code running in the app, so keep
  XSS out of the web apps (CSP + no third-party scripts) and keep the APK free
  of untrusted code. A future hardening step is `androidx.security:security-crypto`
  (Keystore-backed) plus short-lived/refresh tokens on the server.
- **Transport**: all API calls are HTTPS with the custom domain; do not enable
  `usesCleartextTraffic` in the manifest and keep Full (strict) TLS at
  Cloudflare.
- **No client-side authorization decisions**: the UI hides screens by role, but
  every endpoint must still enforce the role server-side (section 3.4).
