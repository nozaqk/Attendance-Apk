# Backend (Fastify + Prisma + PostgreSQL)

Deploy target: **Render** web service. Database: **Neon** PostgreSQL. See `../docs/RENDER_DEPLOYMENT.md`.

```bash
cp .env.example .env        # fill DATABASE_URL, JWT_SECRET, CORS_ORIGINS
npm ci
npm run build               # prisma generate + tsc
npm run db:deploy           # applies prisma/migrations
ADMIN_EMAIL=you@uni.edu ADMIN_PASSWORD='long-password' npm run create-admin
npm start                   # http://localhost:3000/health
```

| Script | Purpose |
|---|---|
| `npm run dev` | watch mode |
| `npm run typecheck` | tsc --noEmit |
| `npm test` | unit + integration tests (integration needs `TEST_DATABASE_URL`, a throwaway Postgres) |
| `npm run start:render` | migrate then start (Render start command) |
| `npm run create-admin` | first ADMIN account (password from env, never printed) |
| `npm run seed:demo` | dev/demo reference data only; refuses in production |

Layout: `src/routes` (HTTP), `src/services` (attendance rules, reports, notifications, mail),
`src/auth` (JWT + role guards), `prisma/` (schema + migrations). API reference: `../docs/API.md`.

Roles: `ADMIN` (staff accounts, students, activity), `QA` (departments, courses, lecturers, rooms,
nodes, class records, reports), `LECTURER`, `STUDENT`. Academic/room/report routes are served under
`/api/qa/*` (and also `/api/admin/*` for admins).

## Custom domain, proxies, CORS and rate limits

The API is meant to sit behind Cloudflare -> Render, e.g. `https://api.attendance.example.com`.
Three environment variables describe that chain, and the app logs the effective values at startup
(`http security configuration`):

| Variable | Default | Meaning |
|---|---|---|
| `CORS_ORIGINS` | *(empty, refused in production)* | Comma-separated bare origins of the web frontends. `*` is refused in production. Requests with **no** `Origin` header - the Android app, curl, the ESP32 nodes - are always allowed. |
| `TRUST_PROXY_HOPS` | `1` | Proxies between the internet and the process. Render's edge is one hop. Set `0` to ignore forwarded headers. |
| `TRUST_CF_CONNECTING_IP` | `true` | Read Cloudflare's `CF-Connecting-IP` as the client address (falls back to the right-most `X-Forwarded-For` entry, then the socket). |
| `RATE_LIMIT_MAX` | `300` | Requests per minute per client IP for the whole API. |

Per-route limits, all bucketed by the real client IP (`req.clientIp`):

| Route | Limit |
|---|---|
| `POST /api/auth/login` | 10 / 15 min |
| `POST /api/auth/register/student` | 20 / hour |
| `POST /api/auth/forgot-password` | 5 / 15 min |
| `POST /api/auth/reset-password` | 10 / 15 min (plus 5 wrong-code tries per account) |
| `POST /api/auth/change-password` | 10 / 15 min |

Exceeding a limit returns HTTP 429 with the usual envelope:

```json
{ "error": { "code": "RATE_LIMITED", "message": "Too many requests. Try again in 15 minutes.", "details": { "max": 10, "retryAfter": "15 minutes" } } }
```

Every `/api/*` route except the public set (`/api/auth/login`, `/api/auth/register/student`,
`/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/public/*`, `/api/nodes/heartbeat`
with its node key) runs behind `authenticate` / `requireRole(...)`, and API responses are sent with
`Cache-Control: no-store`.

Keep the Render service reachable **only** through Cloudflare (Cloudflare DNS proxied, and Render
firewall / Access rule blocking direct traffic), otherwise a caller can forge `CF-Connecting-IP` and
pick its own rate-limit bucket.
