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
| `docs/backend-hardening.js` (new) | drop-in Express module: trust proxy, CORS allowlist, helmet, rate limits, auth gate audit |
| `docs/DOMAIN_AND_API_SECURITY.md` (new) | this document |
| `ANDROID_BUILD.md` | points at the new configuration |

The backend source is **not** in this repository, so section 3 is a precise
patch + checklist for the API repo on Render. `docs/backend-hardening.js` is
ready to copy in.

---

## 3. Backend changes (Render API)

### 3.1 Trust the Cloudflare proxy

```js
// before any middleware that reads req.ip
app.set('trust proxy', 1);   // exactly one hop: Cloudflare → Render
```

- Use `1`, **not** `true`. With `true`, a client can send its own
  `X-Forwarded-For` and bypass IP rate limits.
- If you ever add another proxy in front of Render, raise the number to match.
- Resolve the real IP explicitly for logging/rate limiting:

```js
app.use((req, _res, next) => {
  const cf = req.headers['cf-connecting-ip'];
  const xff = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  req.clientIp = (typeof cf === 'string' && cf.trim()) || xff || req.ip;
  next();
});
```

### 3.2 CORS: web origin only, but allow missing Origin

```js
const allowed = parseOrigins(process.env.CORS_ORIGINS); // "https://yourdomain.com"
app.use(cors({
  origin(origin, cb) {
    if (!origin) return cb(null, true);          // native app / curl / health checks
    if (allowed.includes(origin.replace(/\/+$/, ''))) return cb(null, true);
    return cb(null, false);                       // browser blocks it; no 500
  },
  credentials: false,                             // bearer tokens, not cookies
  allowedHeaders: ['Authorization', 'Content-Type', 'Accept', 'X-Requested-With'],
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  maxAge: 86400,
}));
```

- The Android app sends **no** `Origin` header — that is why the `!origin` branch
  must allow the request. It is not a CSRF hole: CSRF requires a browser
  sending ambient credentials, which this API does not use.
- Add every web origin you actually serve (e.g. the Vercel preview domain)
  to `CORS_ORIGINS`, comma-separated. Do **not** use `*` together with
  credentials, and do not reflect arbitrary origins.

### 3.3 Rate limiting on the public/auth routes

Mount limiters **before** the controllers, after the proxy/real-IP middleware:

```js
const { authLimiter, accountLimiter, resetLimiter, apiLimiter } = buildLimiters();
app.post('/api/auth/login', authLimiter, loginHandler);
app.post('/api/auth/register/student', accountLimiter, registerHandler);
app.post('/api/auth/forgot-password', resetLimiter, forgotHandler);
app.post('/api/auth/reset-password', resetLimiter, resetHandler);
app.use('/api', apiLimiter);           // broad backstop, mounted after the above
```

Suggested starting limits (tune from traffic): login 10 / 15 min / IP with
`skipSuccessfulRequests: true`; account creation and password reset 5–20 / hour /
IP; global 300 / min / IP (the clients poll every 5–60 s). The 429 body must
keep the app's error contract:

```json
{ "error": { "code": "RATE_LIMITED", "message": "Too many attempts…" } }
```

Cloudflare rate-limiting rules (Security → WAF → Rate limiting rules) are a good
second layer on `/api/auth/*`; they use `CF-Connecting-IP` natively.

### 3.4 Authentication coverage

- Every `/api` route except login, student sign-up, forgot/reset password,
  logout, public departments and health must require a valid bearer token and
  the right role (`LECTURER`, `STUDENT`, …).
- Run `assertProtectedRoutes(app)` at boot in staging: it walks the router and
  warns about any `/api` route that is not in the public allowlist, so a new
  route that forgets the auth middleware is caught immediately.
- Reject tokens whose subject does not match the resource (e.g. a student
  reading `/api/lecturer/sessions/:id`), and keep the existing 401 contract so
  clients clear their session.

### 3.5 Response/security headers

- `helmet()` for `X-Content-Type-Options`, `X-Frame-Options`,
  `Referrer-Policy`, HSTS, etc.
- `Cache-Control: no-store` on all API responses (the module does this). This is
  what stops Cloudflare from caching one user's JSON for another user.
- Keep `Access-Control-Expose-Headers: Content-Disposition` if exports are
  downloaded by the web app.

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
| `TRUST_PROXY` | `1` (or hard-code `app.set('trust proxy', 1)`) | must match the proxy hops |
| `RATE_LIMIT_DISABLED` | unset | only set `1` in tests |

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

# 6. Real IP is logged/limited per client, not per proxy
curl -sS -i https://yourdomain.com/api/health -H 'CF-Connecting-IP: 203.0.113.7'
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
