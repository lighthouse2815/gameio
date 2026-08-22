# Deployment infrastructure

The production target is intentionally split by workload:

- `frontend/`: Cloudflare Workers through the OpenNext adapter.
- repository-root `Dockerfile` + `railway.toml`: Railway Hobby in Singapore, one long-running backend replica with Serverless disabled.
- PostgreSQL and Redis: Railway project services connected to the backend over Railway's private network.

This keeps the browser portal close to users at the edge while the stateful Java/WebSocket process remains continuously available.

The current frontend source integration is Cloudflare Workers Builds for `lighthouse2815/gameio`, branch `main`, with Root Directory `frontend`; it publishes the `gameio` Worker at `https://gameio.alexnguyena47.workers.dev`. Keep the successful Workers Build SHA as frontend revision evidence. `npm run deploy` is reserved for an intentional manual recovery from `frontend`, not the routine source-triggered release path.

## Deployment order

1. Provision Railway PostgreSQL and Redis.
2. Deploy the backend and verify `/actuator/health` plus a WebSocket handshake.
3. Set the final backend HTTPS/WSS origins in the frontend build variables.
4. Deploy the OpenNext worker.
5. Replace the backend CORS allow-list with the final Worker hostname and run production smoke tests.

## Railway source contract

The current production service is connected to `lighthouse2815/gameio`, branch `main`, with Root Directory left empty so Railway reads the root `railway.toml` and root `Dockerfile`. The Dockerfile copies and builds `backend/`; Railway must not be configured with Root Directory `backend` while still pointing at the root Dockerfile. Keep autodeploy enabled and either leave Watch Paths empty or include `Dockerfile`, `railway.toml` and `backend/**`.

The manifest configures a build and health check only; it does not create a GitHub trigger. GitHub Actions verifies the repository but contains no Railway deploy job. If the production SHA stops advancing, inspect/reconnect Railway **Settings → Source**, verify `main` and the repository-root context, and resolve any **Wait for CI** failure before redeploying the reviewed SHA.

Do not accept `/actuator/health = UP` as version evidence. The release gate also requires the Railway deployment SHA to match `origin/main`, Flyway migration V13 to be successful and `/api/games?page=0&size=50` to contain all eighteen catalog slugs. The Cloudflare BFF must return the same catalog, and the frontend CSP must treat `/api` as same-origin without allowing `http://localhost:8080`.

No production credential belongs in this directory or in Git history.
