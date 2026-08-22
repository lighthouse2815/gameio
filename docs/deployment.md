# Production deployment

Gameio separates the edge web portal from the stateful Java/WebSocket process:

- frontend: Cloudflare Workers via `@opennextjs/cloudflare`;
- backend: Railway, one continuously running Singapore replica with Serverless disabled;
- data: PostgreSQL and Redis services in the same Railway project/private network.

Railway is preferred to a free Render service because an authoritative multiplayer JVM and its WebSocket endpoint must not wait for a sleeping service to wake. A paid non-sleeping Render web service remains the fallback if Railway cannot be used.

Deployment is complete only after both scripted live smoke flows and real browser QA pass. Provisioning services or receiving a platform URL is not sufficient evidence.

## 1. Preflight

Run from the repository root before deploying a commit:

```powershell
Set-Location backend
cmd /c .\mvnw.cmd -B -ntp clean verify

Set-Location ..\frontend
npm ci --no-audit --no-fund
npm run check
npm run cf:build

Set-Location ..
docker compose config --quiet
docker compose build
git diff --check

$releaseSha = git rev-parse HEAD
$remoteMainSha = (git ls-remote origin refs/heads/main).Split()[0]
if ($releaseSha -ne $remoteMainSha) { throw "Local HEAD is not the pushed main revision" }
```

Do not deploy if any command fails. Record `$releaseSha` as the reviewed immutable revision for both providers and for rollback.

## 2. Provision Railway data services

Create one Railway project and add PostgreSQL plus Redis. Keep both databases on private networking and do not publish their ports.

Before real player data is accepted:

1. configure PostgreSQL backups and retention;
2. keep an off-platform recovery copy appropriate to the environment;
3. perform a restore drill and record the result;
4. treat Redis as disposable rather than as a backup of persistent data.

Railway's database templates are application services rather than a guarantee of managed HA. Availability, backup and restore remain an operator responsibility.

## 3. Deploy the Spring backend

The current Railway service builds from the repository root, not from `backend/`. Leave **Root Directory** empty (repository root, sometimes displayed as `/`). The root [Dockerfile](../Dockerfile) copies `backend/pom.xml` and `backend/src`, builds the Spring JAR, and produces the non-root Java runtime image. The root [railway.toml](../railway.toml) selects that Dockerfile, `/actuator/health`, a 180-second health timeout and bounded restart-on-failure behavior. `backend/railway.toml` supports a separate backend-only build context, but it is not the source path used by the current production service; do not mix the two contexts.

Configure the service:

- source repository: `lighthouse2815/gameio`;
- production branch: `main`;
- Root Directory: empty/repository root;
- autodeploy: enabled;
- Watch Paths: empty, or explicitly include `Dockerfile`, `railway.toml` and `backend/**`;
- region: Singapore;
- replicas: exactly one;
- Serverless/sleep: disabled;
- health path: `/actuator/health`;
- public HTTPS domain enabled (also used as the WSS host).

Set runtime variables in Railway's encrypted variable store or by service references:

```text
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=<private PostgreSQL connection URL>
REDIS_URL=<private Redis connection URL>
JWT_SECRET=<at least 64 random bytes>
METRICS_TOKEN=<separate random value of at least 32 bytes>
JWT_ISSUER=gameio-api
JWT_ACCESS_TTL=15m
JWT_REFRESH_TTL=30d
GOOGLE_CLIENT_ID=<google-web-client-id>.apps.googleusercontent.com
CORS_ALLOWED_ORIGINS=https://<cloudflare-worker-host>
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=Lax
REFRESH_COOKIE_DOMAIN=
```

Railway supplies `PORT`. Do not set `PORT` to a fixed value in production. The backend accepts `postgres://`, `postgresql://` and JDBC PostgreSQL URLs and extracts embedded credentials; `DB_URL`/`JDBC_DATABASE_URL`, `DB_USERNAME`/`PGUSER` and `DB_PASSWORD`/`PGPASSWORD` are supported alternatives.

For Redis, `REDIS_URL` is preferred. When a provider does not supply it, configure `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD` and `REDIS_SSL` explicitly.

The `prod` profile refuses a missing/default `JWT_SECRET` or `METRICS_TOKEN`. Keep them independent and do not weaken either guard to make a failed deployment start.

Verify the backend before building the frontend:

```powershell
Invoke-RestMethod https://<railway-host>/actuator/health
```

Expected result: `status` is `UP`, Flyway completed, and Railway reports the replica healthy.

### Verify the deployed revision and recover autodeploy

`railway.toml` describes how Railway builds and starts a revision; it does not create the GitHub source trigger. `.github/workflows/ci.yml` is a quality gate and does not deploy the backend. Cloudflare Workers Builds and Railway each use their own provider-native GitHub connection.

In the Railway deployment details, confirm that the source commit equals the recorded `$releaseSha`. An `UP` health response proves only that some compatible process is running. It does not prove the process contains the current migrations or engines.

A characteristic version-skew symptom is a current Cloudflare frontend showing new game renderers while Railway `/api/games` still returns an older catalog. To recover safely:

1. compare the successful Cloudflare Workers Build SHA, Railway deployment SHA and `origin/main` SHA;
2. in Railway **Settings → Source**, reconnect `lighthouse2815/gameio`, branch `main`, if the deployment SHA stopped advancing;
3. restore the repository-root configuration and Watch Paths listed above;
4. if **Wait for CI** is enabled, fix the failing GitHub jobs instead of bypassing the gate; if successful commits also produced no Railway deployment, reconnect the source trigger;
5. deploy the reviewed `main` SHA and confirm Railway labels the new deployment with that exact SHA;
6. retain the previous immutable deployment until the schema, catalog, smoke and browser checks below pass.

Check Flyway from the Railway PostgreSQL query console:

```sql
select installed_rank, version, description, success
from flyway_schema_history
order by installed_rank desc
limit 5;
```

For this release the newest successful migration must be `13`, `add online board game collection`. Then verify the backend catalog rather than inferring migration success from health:

```powershell
$backendOrigin = "https://<railway-host>"
$catalog = Invoke-RestMethod "$backendOrigin/api/games?page=0&size=50"
$expectedSlugs = @(
  "2048", "breakout", "caro", "connect-four", "dots-and-boxes", "flappy-bird",
  "hex", "mancala", "memory-match", "minesweeper", "reversi",
  "rock-paper-scissors", "snake", "sos", "tank-battle", "tic-tac-toe",
  "typing-race", "ultimate-tic-tac-toe"
)
$actualSlugs = @($catalog.content.slug)
$missing = @($expectedSlugs | Where-Object { $_ -notin $actualSlugs })
if ($catalog.totalElements -ne 18 -or $missing.Count) {
  throw "Railway catalog is not the expected V13 catalog: missing $($missing -join ', ')"
}
```

Verify the protected metrics endpoint without printing the token:

```powershell
$metricsHeaders = @{ "X-Gameio-Metrics-Token" = $env:METRICS_TOKEN }
(Invoke-WebRequest https://<railway-host>/actuator/prometheus -Headers $metricsHeaders).StatusCode
```

Expected result: `200`. A missing or incorrect header returns `401`. The endpoint exposes JVM, HTTP, database-pool and low-cardinality `gameio_realtime_*` metrics; it never labels data with a room ID, user ID or token. See the [operations runbook](operations.md) for alerts and backup/restore drills.

## 4. Configure the Cloudflare/OpenNext frontend

The frontend is not a static Pages export. OpenNext builds a Worker that serves Next.js and the same-origin API proxy.

The current production frontend is the `gameio` Worker at `https://gameio.alexnguyena47.workers.dev`. Its routine releases come from Cloudflare Workers Builds connected to `lighthouse2815/gameio`, branch `main`, with `frontend` as the root directory. Keep that native source integration enabled so the Cloudflare deployment details retain the source commit SHA; that SHA must match the reviewed `$releaseSha`. The local `npm run deploy` command below is the manual recovery/equivalent path, not a replacement for the Git trigger.

At build time set:

```text
NEXT_PUBLIC_API_URL=/api
NEXT_PUBLIC_WS_URL=wss://<railway-host>/ws
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<same-google-web-client-id>.apps.googleusercontent.com
```

At Worker runtime set:

```text
BACKEND_ORIGIN=https://<railway-host>
```

`NEXT_PUBLIC_*` values are intentionally public browser configuration and are inlined during `next build`. `BACKEND_ORIGIN` is read only by the Worker route and must be a plain HTTPS origin without credentials, an application path, query or fragment. The proxy never accepts a caller-selected upstream.

`NEXT_PUBLIC_API_URL=/api` is deliberately relative. It therefore contributes no external host to CSP `connect-src`; same-origin API traffic is covered by `'self'`. The production CSP must contain the exact Railway WSS origin and Google Identity Services parent URL, and must not contain `http://localhost:8080`.

For a deliberate manual recovery, build and deploy from `frontend`:

```powershell
Set-Location frontend
$env:NEXT_PUBLIC_API_URL = "/api"
$env:NEXT_PUBLIC_WS_URL = "wss://<railway-host>/ws"
$env:NEXT_PUBLIC_GOOGLE_CLIENT_ID = "<same-google-web-client-id>.apps.googleusercontent.com"
npm run deploy
```

Configure `BACKEND_ORIGIN` through the Worker environment before serving traffic. Keep preview and production bindings separate. A custom domain can be attached later, but the default `workers.dev` origin works with the same-origin BFF.

After deploy, compare the BFF catalog and CSP with the direct backend result:

```powershell
$frontendOrigin = "https://<cloudflare-host>"
$edgeCatalog = Invoke-RestMethod "$frontendOrigin/api/games?page=0&size=50"
if ($edgeCatalog.totalElements -ne 18) { throw "Cloudflare BFF is not serving the V13 catalog" }

$csp = (Invoke-WebRequest "$frontendOrigin/").Headers["Content-Security-Policy"]
if (-not $csp -or $csp -match "http://localhost:8080") {
  throw "Production CSP is missing or still allows the local backend origin"
}
```

After the final Worker hostname is known, set Railway `CORS_ALLOWED_ORIGINS` to that exact HTTPS origin and redeploy/restart the backend if required. No wildcard, localhost or broad subdomain pattern belongs in the production allow-list.

Create separate Google Cloud projects/clients for development and production. Use an OAuth 2.0 client of type **Web application**, complete its consent/branding configuration, and add the exact frontend origin under **Authorized JavaScript origins**. Add `http://localhost:3000` only to the development client. This callback-based Google Identity Services flow does not require an Authorized redirect URI. The public client ID must be identical in the frontend build variable and Railway backend variable; changing the frontend value requires a new build.

Before opening Google sign-in to the public, attach a custom domain owned by the Gameio operator rather than relying on the shared `workers.dev` provider domain. Verify that domain through Google Search Console, host a public Gameio home page and an accurate privacy policy on it, then publish/verify the OAuth brand as required. Google requires production JavaScript origins and app-domain links to use domains the operator can verify; the development client can remain in Testing status with explicitly listed test users until this release gate is complete.

## 5. Cookie, CORS and BFF contract

Production browser REST flow:

```text
Browser https://gameio...workers.dev/api/*
  -> Cloudflare fixed BACKEND_ORIGIN proxy
  -> https://...railway.app/api/*
```

The proxy forwards the request method, query, body, authorization/CSRF headers and cookies; preserves `Set-Cookie`; strips hop-by-hop/host headers; follows no caller-controlled origin; rejects request bodies over 1 MiB; and uses `cache: no-store`. The browser therefore stores `gameio_refresh` as a first-party, host-only, `HttpOnly; Secure; SameSite=Lax; Path=/api/auth` cookie on the frontend host.

WebSocket flow is direct:

```text
Browser -> wss://<railway-host>/ws
```

The backend validates the Cloudflare page Origin during that handshake. Access-token authentication uses WebSocket subprotocols, not a URL query parameter.

## 6. Scripted live verification

The scripts call the backend directly so they verify its health, credentialed CORS and cookie attributes. They never print access/refresh tokens or passwords.

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

foreach ($slug in @(
  "typing-race", "connect-four", "reversi", "rock-paper-scissors",
  "ultimate-tic-tac-toe", "dots-and-boxes", "mancala", "hex", "sos"
)) {
  .\scripts\realtime-smoke.ps1 `
    -GameSlug $slug `
    -BaseUrl https://<railway-host> `
    -WsUrl wss://<railway-host>/ws `
    -Origin https://<cloudflare-host> `
    -SkipDockerServices
}
```

The first script verifies health, registration, login, credentialed CORS, refresh-cookie rotation, logout and refresh rejection. Each realtime run uses two users and two sockets to verify JWT subprotocol negotiation, room create/join/ready/start, genuine game-specific actions, the authoritative terminal state and durable outcomes. The default run covers Tic Tac Toe; the loop covers every other script-supported path.

Both scripts create unique durable smoke accounts; the realtime script also creates match results. Use a production-safe test-account retention policy because Gameio intentionally has no destructive account-delete endpoint.

## 7. Browser release gate

Use the deployed Cloudflare URL, not localhost, and verify all of the following:

- password register/login, first-time Google registration, returning Google login, refresh after a page load and logout;
- Google account chooser popup/FedCM completion on both `/login` and `/register`, including an invalid-token response and an existing-email collision case;
- request cookies are first-party on the Cloudflare host and are not exposed to JavaScript;
- no direct Railway REST call from the browser API client;
- catalog, profiles, friends, rankings, light/dark mode and responsive navigation;
- real 2048, Snake, Flappy Bird, Breakout, Minesweeper and Memory Match input on desktop/mobile controls, including offline and signed-in verified modes;
- two isolated browser sessions completing Tic Tac Toe, Caro, Connect Four, Reversi, Rock Paper Scissors, Ultimate Tic Tac Toe, Dots and Boxes, Mancala, Hex and SOS;
- Tank Battle movement/shoot/state updates with two sessions;
- reconnect through a controlled backend restart, `ROOM_EXPIRED` fallback for a removed/corrupt checkpoint and no blank canvas on socket failure;
- no console errors, failed mixed-content requests, CSP violations or unexpected cached authenticated responses.

Record the frontend/backend URLs, commit SHA, Flyway version, test timestamp and screenshots. Never record credentials or tokens.

## 8. Rollback

Keep the previous immutable backend deployment and frontend Worker version available. An application rollback must remain schema-compatible with all migrations already applied; Flyway migrations are not rolled back by redeploying old code. If a database restore is required, follow the tested restore runbook and reconcile any writes made after the recovery point before reopening traffic.

Official platform references:

- [Cloudflare Next.js on Workers](https://developers.cloudflare.com/workers/framework-guides/web-apps/nextjs/)
- [OpenNext Cloudflare environment variables](https://opennext.js.org/cloudflare/howtos/env-vars)
- [Railway Serverless setting](https://docs.railway.com/deployments/serverless)
- [Railway regions](https://docs.railway.com/deployments/regions)
- [Railway Spring Boot guide](https://docs.railway.com/guides/spring-boot)
