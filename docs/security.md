# Security and release checklist

Run this checklist for production and every internet-accessible preview. Preview environments must use separate data stores, secrets and cookie/CORS origins.

## Accounts and secrets

- [ ] Require MFA for GitHub, Cloudflare, Railway and database-provider accounts; grant the smallest practical role.
- [ ] Keep production values only in provider variables/secrets. Do not commit `.env`, paste credentials into commands captured by logs, or put secrets in `NEXT_PUBLIC_*` values.
- [ ] Generate a unique `JWT_SECRET` from at least 64 random bytes. The application enforces at least 32 UTF-8 bytes and the `prod` profile rejects the repository fallback.
- [ ] Use unique PostgreSQL and Redis credentials. Rotate them after personnel/provider access changes or suspected disclosure.
- [ ] Record secret owners and rotation dates. Rotating the JWT signing secret intentionally invalidates every access token.

## Railway backend boundary

- [ ] Keep PostgreSQL and Redis on private networking with no public port. Expose only backend HTTPS/WSS.
- [ ] Set `SPRING_PROFILES_ACTIVE=prod`; allow Railway to supply `PORT`; use `/actuator/health` as the health check.
- [ ] Keep actuator exposure limited to public health and non-sensitive info. Never expose environment, heap, beans or configuration endpoints.
- [ ] Run exactly one Singapore backend replica with Railway Serverless/sleep disabled. Active engine state is process-local and is not safe to distribute across replicas.
- [ ] Set `CORS_ALLOWED_ORIGINS` to exact HTTPS frontend origins. Do not use `*`, broad subdomain patterns or localhost in production.
- [ ] Terminate TLS at the platform edge and use only `https://`/`wss://` public URLs.
- [ ] Add edge limits for registration, login, refresh and WebSocket connection attempts. Application login limiting is process-local (10 failed attempts per client key in one minute) and is not a distributed abuse defense.

## Browser authentication

- [ ] Keep the access token in memory only. Never place it in `localStorage`, cookies, URLs, analytics, error reports or console logs.
- [ ] Keep refresh tokens only in the backend-issued HttpOnly cookie; auth JSON must not expose the raw value.
- [ ] Set `REFRESH_COOKIE_SECURE=true`, `REFRESH_COOKIE_SAME_SITE=Lax` and leave `REFRESH_COOKIE_DOMAIN` empty for the Cloudflare same-origin BFF.
- [ ] Preserve `Path=/api/auth` and require `X-Gameio-CSRF: 1` on every auth POST.
- [ ] Verify refresh rotation, used-token rejection and family revocation on logout against production.
- [ ] Keep access-token lifetime short. Logout revokes the refresh family, but a captured access token remains cryptographically valid until expiry.
- [ ] On logout, the browser must clear its in-memory token/query cache and close the active WebSocket. The backend independently closes established sockets when their token expires.
- [ ] Keep `GOOGLE_CLIENT_ID` and `NEXT_PUBLIC_GOOGLE_CLIENT_ID` equal to the same Web application client ID. The value is a public identifier, not a secret; never configure a client secret in the browser or backend for this ID-token flow.
- [ ] Register every exact frontend scheme/host/port under Google Authorized JavaScript origins, and remove obsolete preview/localhost origins from the production client when they are no longer needed.
- [ ] Keep development and production in separate Google Cloud projects. For public production, use a custom domain the operator owns and can verify, host an accurate home/privacy surface there, and complete Google OAuth brand publication before enabling the client ID for all users.
- [ ] Verify Google ID-token signature, issuer, expiry, audience and `email_verified` on the backend. Resolve returning users by the immutable Google `sub`; never trust browser-decoded profile fields.
- [ ] Never link a Google identity to an existing account from an unauthenticated email match, including Gmail or Workspace. Require an already-authenticated account-link flow and explicit confirmation if linking is implemented later.

## Cloudflare BFF

- [ ] Build with `NEXT_PUBLIC_API_URL=/api`; set `NEXT_PUBLIC_WS_URL` to the exact Railway `wss://.../ws` endpoint.
- [ ] Configure Worker runtime `BACKEND_ORIGIN=https://<railway-host>` without credentials, path, query or fragment.
- [ ] Confirm `/api/[...path]` constructs the upstream from that fixed binding only. It must never accept an upstream URL from a request header, query or body.
- [ ] Preserve request cookies, `Authorization`, `Content-Type` and `X-Gameio-CSRF`, and preserve upstream `Set-Cookie` responses.
- [ ] Strip host, forwarding and hop-by-hop headers; use manual redirect handling and `cache: no-store` for every proxied response.
- [ ] Reject a declared or actual proxy request body over 1 MiB with a generic `413 REQUEST_TOO_LARGE` response.
- [ ] Keep preview and production bindings separate so a preview cannot mutate production data.
- [ ] Verify browser REST requests stay on the Cloudflare origin. The Railway REST URL must not be the production `NEXT_PUBLIC_API_URL`.

## Frontend response headers

- [ ] Verify production responses include CSP, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Content-Type-Options: nosniff`, frame denial, opener/resource policies and a restrictive Permissions Policy.
- [ ] Confirm production CSP `connect-src` includes only self, the exact WSS backend origin required by the build and the Google Identity Services parent URL; restrict Google `script-src`, `style-src` and `frame-src` allowances to the documented GIS endpoints.
- [ ] Keep `object-src 'none'`, `frame-ancestors 'none'`, `base-uri 'self'` and `form-action 'self'`.
- [ ] `unsafe-eval` is permitted only during development. Next.js currently requires an inline-script/style allowance; do not broaden other directives to compensate for a CSP error.
- [ ] Keep `Cross-Origin-Opener-Policy: same-origin-allow-popups` while the GIS popup fallback is supported, and test Phaser, local fonts, Web Workers, images, Next hydration and the Google account chooser under the deployed headers.

## WebSocket and authoritative gameplay

- [ ] Authenticate `/ws` through `gameio.v1` plus `gameio.jwt.<access-token>` subprotocols. Never accept a token in the URL query string.
- [ ] Confirm the server negotiates only `gameio.v1`, validates the exact page Origin and rejects missing/duplicate/expired JWT protocols.
- [ ] Enforce token expiry after connection, per-user limits (120 messages and 60 game inputs/second), per-IP limits (240 messages/second and 60 handshakes/minute), 20 handshakes/user/minute, a 60-second reconnect grace, five-minute idle limit and 30-minute maximum match duration.
- [ ] Reject unknown envelope/payload fields, oversized text frames, binary frames and a client-supplied identity.
- [ ] Validate room membership, owner-only start, turn, coordinates, occupancy and monotonically increasing Tank input sequence.
- [ ] Require the current socket to bind through `ROOM_JOIN` before room/game commands, and prevent one user from binding active sockets to different rooms.
- [ ] Accept only actions. Never accept a client-authoritative score, position, rotation, HP, bullet hit or terminal outcome.
- [ ] Persist terminal multiplayer results once by match ID before publishing `GAME_OVER`.
- [ ] Treat process restart as loss of active engine state: delete stale playing rooms and return `ROOM_EXPIRED` instead of inventing a restored state.

## Data and recovery

- [ ] Use Flyway migrations and keep Hibernate `ddl-auto=validate`; never enable create/update in production.
- [ ] Use a least-privilege application database role when the provider permits migration/runtime separation.
- [ ] Enable encrypted PostgreSQL backups with retention and at least one appropriate off-platform copy.
- [ ] Perform a restore drill before launch and on a schedule. A backup is not verified until a restore succeeds.
- [ ] Treat Redis as ephemeral. Account, result, EXP, achievement, friendship and leaderboard recovery must not depend on Redis; cached rankings must fail open to PostgreSQL.
- [ ] Define retention/deletion rules for accounts, smoke-test users, match history, abuse metadata and operational logs.

## Logging and incident response

- [ ] Redact `Authorization`, subprotocol JWTs, passwords, cookies, refresh/access tokens and database connection strings from application, proxy, CI, analytics and error-reporting logs.
- [ ] Alert on sustained auth failures, refresh-token reuse, abnormal socket volume, 5xx spikes, Flyway failure and database saturation.
- [ ] Return generic auth/internal errors. Production responses must not contain stack traces or exception class names.
- [ ] Prepare a runbook for secret rotation, session invalidation, abuse blocking, database restore, rollback and player communication.

## Supply chain and CI

- [ ] Review Maven Wrapper files and `package-lock.json`; install frontend dependencies with `npm ci`.
- [ ] Require `.github/workflows/ci.yml`: backend Maven `verify`, frontend `npm run check`, Cloudflare build, Compose validation and container build.
- [ ] Enable dependency/container alerts and review major runtime/base-image changes.
- [ ] Protect the production branch and keep deployment credentials out of pull-request CI.
- [ ] Map every deployment to an immutable reviewed commit and retain the previous rollback target.

## Release verification

Local full-stack checks:

```powershell
docker compose up --build -d
.\scripts\smoke.ps1
.\scripts\realtime-smoke.ps1
```

Live direct-backend checks:

```powershell
.\scripts\smoke.ps1 `
  -BaseUrl https://<railway-host> `
  -Origin https://<cloudflare-host> `
  -SkipDockerServices

.\scripts\realtime-smoke.ps1 `
  -BaseUrl https://<railway-host> `
  -WsUrl wss://<railway-host>/ws `
  -Origin https://<cloudflare-host> `
  -SkipDockerServices
```

- [ ] Confirm both scripts pass without printing credentials. They create durable smoke users/results; apply the documented retention policy.
- [ ] In the deployed browser, verify BFF cookie persistence/rotation, logout, CORS, CSP, mixed-content behavior and console/network output.
- [ ] Complete real 2048/Snake input, Flappy Bird offline/verified input, and two-session Tic Tac Toe, Caro and Tank flows.
- [ ] Record successful timestamp, frontend/backend URLs, commit, Flyway version, screenshots and rollback target without recording secrets.
