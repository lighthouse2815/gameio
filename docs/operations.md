# Gameio operations runbook

This runbook covers health checks, Prometheus collection, active-match recovery, PostgreSQL backup and restore. PostgreSQL remains the durable source of truth. Redis checkpoints improve continuity for matches in progress, but Redis is not a replacement for a database backup.

## Health and metrics

The backend exposes these actuator surfaces only:

| Endpoint | Access | Purpose |
| --- | --- | --- |
| `/actuator/health` | Public | Platform health check |
| `/actuator/health/liveness` | Public | JVM/application liveness |
| `/actuator/health/readiness` | Public | Dependency-aware readiness |
| `/actuator/info` | Normal application authentication | Non-sensitive build/application info |
| `/actuator/prometheus` | `X-Gameio-Metrics-Token` | Prometheus scrape payload |

Production requires a dedicated `METRICS_TOKEN` containing at least 32 UTF-8 bytes and rejects the repository fallback. It must differ from `JWT_SECRET`, database credentials and user access tokens. Store it in the deployment provider's secret store and in the collector's secret configuration; never put it in a URL, browser bundle or dashboard query.

PowerShell verification:

```powershell
$backendOrigin = "https://<railway-host>"
Invoke-RestMethod "$backendOrigin/actuator/health"
$metricsHeaders = @{ "X-Gameio-Metrics-Token" = $env:METRICS_TOKEN }
$metrics = Invoke-WebRequest "$backendOrigin/actuator/prometheus" -Headers $metricsHeaders
$metrics.StatusCode
```

In addition to standard JVM, HTTP and connection-pool meters, Gameio publishes low-cardinality realtime signals:

- `gameio_realtime_websocket_connections`: current authenticated socket count;
- `gameio_realtime_matches_active`: active engines owned by this process;
- `gameio_realtime_matches_total{state="started|completed"}`: lifecycle counts;
- `gameio_realtime_matches_duration_seconds`: completed-match duration histogram;
- `gameio_realtime_inputs_accepted_total`: accepted player inputs;
- `gameio_realtime_match_restores_total{outcome="success|missing|invalid"}`: restart recovery outcomes;
- `gameio_realtime_checkpoints_total{operation="save|delete",outcome="success|failure"}`: Redis checkpoint operations;
- `gameio_realtime_websocket_send_failures_total`: serialization or delivery failures.

No custom metric label contains a user ID, room ID, match ID, token or free-form value.

Recommended initial alerts:

- health/readiness is down for two consecutive checks;
- any checkpoint save failure in five minutes;
- any invalid checkpoint restore in fifteen minutes;
- sustained WebSocket send failures;
- HTTP 5xx rate above the normal baseline;
- PostgreSQL or Redis health degradation, pool saturation or disk pressure;
- Flyway/startup failure or repeated process restarts.

Tune thresholds from observed traffic rather than guessing a player-count target. A `missing` restore can be an expired/stale client and is weaker than an `invalid` restore; alert only if its rate becomes abnormal.

## Active-match restart recovery

The running process owns timers and WebSocket delivery. After every accepted state transition, it writes an exact TTL-bound Redis checkpoint containing:

- match and room identity;
- ordered players and game slug;
- start, last-activity and disconnect times;
- the game-specific authoritative engine state.

The first reconnect or spectate request after a process restart validates the checkpoint against the current `PLAYING` room, recreates the engine and sends a fresh `GAME_STATE`. Players not yet connected to the new process enter the same 60-second reconnect grace used for an ordinary disconnect. A terminal checkpoint is completed through the idempotent durable result path before new input is accepted.

If Redis is unavailable, new rooms, matchmaking and checkpoint writes are affected. Existing in-memory matches may continue temporarily, but restart recovery is no longer guaranteed. Do not deliberately restart while checkpoint-save failures are active. Once Redis returns, verify health, checkpoint-success metrics and one controlled non-production reconnect.

If a checkpoint is missing, expired, corrupt, incompatible or mismatched with room metadata, it is discarded and the client receives `ROOM_EXPIRED`. This is the fail-safe path: never manufacture a board, turn, Tank position or result.

Checkpoint recovery does not make active matches horizontally distributable. Keep exactly one realtime backend replica until the system has lease/fencing ownership, cross-replica socket fan-out and duplicate-tick prevention.

## Create a PostgreSQL backup

The backup script creates a PostgreSQL custom-format dump, validates it with `pg_restore --list`, confirms it is non-empty and writes a SHA-256 sidecar. Its default destination is the git-ignored `.data/backups` directory.

Local Docker Compose:

```powershell
Set-Location <gameio-repository>
.\scripts\backup-postgres.ps1 -Mode Docker
```

Direct/provider PostgreSQL uses standard libpq environment variables so the password is not passed as a command-line argument:

```powershell
$env:PGHOST = "<private-database-host>"
$env:PGPORT = "5432"
$env:PGDATABASE = "gameio"
$env:PGUSER = "<backup-role>"
$env:PGPASSWORD = "<read-from-secret-store>"
$env:PGSSLMODE = "require"
.\scripts\backup-postgres.ps1 -Mode Direct -Label gameio-production
```

After backup:

1. retain the `.dump` and matching `.dump.sha256` together;
2. encrypt the storage destination and keep at least one appropriate off-platform copy;
3. apply a documented retention policy;
4. record timestamp, database environment, script/commit version, byte size and restore-drill result without recording credentials;
5. run an isolated restore drill. A successful dump command alone is not recovery proof.

## Inspect and restore a backup

Inspection verifies the checksum and archive table of contents without modifying a database:

```powershell
.\scripts\restore-postgres.ps1 `
  -BackupPath .\.data\backups\gameio-<timestamp>.dump `
  -Mode Docker `
  -ListOnly
```

The safest drill restores into a separate empty database, never the active application database:

```powershell
docker compose exec -T postgres createdb --username gameio gameio_restore_drill
.\scripts\restore-postgres.ps1 `
  -BackupPath .\.data\backups\gameio-<timestamp>.dump `
  -Mode Docker `
  -DatabaseName gameio_restore_drill `
  -ConfirmRestore
docker compose exec -T postgres psql --username gameio --dbname gameio_restore_drill `
  --command "select version, success from flyway_schema_history order by installed_rank desc limit 5;"
```

Verify expected row counts and representative account, catalog, result, progression, friendship, season and tournament queries in the isolated database. Record the result. Remove the exact drill database only after the evidence is captured:

```powershell
docker compose exec -T postgres dropdb --username gameio gameio_restore_drill
```

For a real recovery into an existing target:

1. declare maintenance and stop application writes;
2. capture a final backup of the damaged/current state when possible;
3. verify the selected dump and checksum with `-ListOnly`;
4. confirm the exact target host and database using provider controls and libpq variables;
5. run `restore-postgres.ps1 -Mode Direct -Clean -ConfirmRestore`; `-Clean` removes objects represented by the archive before recreating them;
6. start one backend replica and confirm Flyway/schema validation, `/actuator/health`, authentication and smoke tests;
7. reconcile writes newer than the recovery point before reopening traffic;
8. rotate credentials if the incident involved possible secret exposure.

`-ConfirmRestore` is always required for a write. Missing checksums are rejected unless the operator explicitly supplies `-AllowMissingChecksum` after performing an independent integrity check. The script never creates a database and never uses `--create`; the operator must select and provision the exact target first.

## Post-deploy verification

For every backend release:

```powershell
$releaseSha = git rev-parse HEAD
# Confirm the Railway deployment details show this exact source SHA before testing.
Invoke-RestMethod https://<railway-host>/actuator/health
$catalog = Invoke-RestMethod "https://<railway-host>/api/games?page=0&size=50"
if ($catalog.totalElements -ne 18) { throw "Expected the Flyway V13 catalog with 18 games" }
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

Also query `flyway_schema_history` through the Railway PostgreSQL console and require successful migration V13. Compare the Cloudflare BFF catalog with the direct Railway catalog, then inspect the frontend CSP: `/api` must remain same-origin, `connect-src` must include the exact Railway WSS origin and must not contain `http://localhost:8080`. A healthy process serving an old catalog is a stale deployment, not a successful release; follow the autodeploy recovery procedure in [deployment.md](deployment.md#verify-the-deployed-revision-and-recover-autodeploy).

Then verify one controlled active-match restart in a non-production environment, confirm a `success` restore metric, and check that neither backend nor browser logs contain credentials, cookies, JWT subprotocol values or metrics tokens. The smoke scripts create durable test users/results; apply the documented retention policy.

For the local Docker stack, the controlled recovery flow is automated:

```powershell
.\scripts\realtime-smoke.ps1 -RestartBackendAfterFirstMove
```

It checkpoints the first Tic Tac Toe move, gracefully closes both sockets, restarts only the backend container, reconnects both players, verifies sequence `1`, the preserved `X` at row `0`/column `0` and the guest's turn, then completes and checks the original match ID in both durable histories.
