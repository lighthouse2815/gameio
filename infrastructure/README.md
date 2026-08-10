# Deployment infrastructure

The production target is intentionally split by workload:

- `frontend/`: Cloudflare Workers through the OpenNext adapter.
- `backend/`: Railway Hobby in Singapore, one long-running replica with Serverless disabled.
- PostgreSQL and Redis: Railway project services connected to the backend over Railway's private network.

This keeps the browser portal close to users at the edge while the stateful Java/WebSocket process remains continuously available.

## Deployment order

1. Provision Railway PostgreSQL and Redis.
2. Deploy the backend and verify `/actuator/health` plus a WebSocket handshake.
3. Set the final backend HTTPS/WSS origins in the frontend build variables.
4. Deploy the OpenNext worker.
5. Replace the backend CORS allow-list with the final Worker hostname and run production smoke tests.

No production credential belongs in this directory or in Git history.
