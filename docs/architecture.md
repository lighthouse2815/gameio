# Gameio architecture

Gameio is a modular monolith with a separately deployable Next.js portal. The backend owns identity, durable player data, progression, social relationships, rooms, matchmaking and authoritative multiplayer rules. This keeps the first release operationally simple while preserving explicit boundaries for later extraction.

## Production topology

```text
Cloudflare Worker / OpenNext
  |-- serves Next.js routes and static assets
  `-- fixed-origin /api/* BFF
          | HTTPS
          v
Railway: Spring Boot, one warm Singapore replica
  |-- /api/* REST
  |-- /ws WebSocket (browser connects directly over WSS)
  |-- PostgreSQL service over private networking
  `-- Redis service over private networking
```

Browser REST calls use the Cloudflare origin (`NEXT_PUBLIC_API_URL=/api`). The BFF forwards only `/api/*` to `BACKEND_ORIGIN`, preserves the backend `Set-Cookie` header, rejects request bodies over 1 MiB and disables caching. This makes the refresh cookie first-party on the portal host.

The browser connects directly to the Railway `/ws` endpoint because the Spring process owns room membership and the active engine. The WebSocket handshake validates the exact page Origin and authenticates through subprotocol values; credentials are not placed in the URL.

## Data ownership

| Store | Data | Recovery expectation |
| --- | --- | --- |
| PostgreSQL | users, hashed refresh tokens, catalog, results, stats, achievements, friendships | Durable source of truth; Flyway-managed and backed up |
| Redis | online/current-game presence, matchmaking tickets/queues, room metadata, 30-second leaderboard responses | Ephemeral and TTL-bound; loss must not erase durable player data |
| Spring process memory | active Tic Tac Toe, Caro and Tank engine instances | Available only while the single backend process remains alive |
| Browser memory | short-lived access token and UI state | Disposable; restored through the HttpOnly refresh cookie and server queries |

Leaderboards query PostgreSQL through a dedicated read repository and use a versioned Redis read-through cache keyed by global/game scope, page and size. Entries expire after 30 seconds. A committed result increments global and affected-game generations; a Redis error fails open to PostgreSQL rather than failing the public ranking request.

## HTTP and authentication boundary

Controllers accept validated DTOs and derive the authenticated player from the verified JWT/SecurityContext. They do not accept a trusted `userId` field. JSON errors use a stable code/message/path envelope, and production responses do not expose stack traces.

Access tokens are short-lived JWTs. Refresh tokens are random opaque values stored only as hashes in PostgreSQL; the raw value exists only in an HttpOnly cookie. Refresh rotates the token under a database lock, and reuse revokes the family. The access token is intentionally not persisted in browser storage.

Google Identity Services is an additional identity entry point, not a second session model. The browser sends the Google ID token to `/api/auth/google`; Spring validates the Google signature and claims, resolves the immutable provider subject stored in PostgreSQL, and then issues the same Gameio access-token/refresh-cookie pair used by password authentication. Provider-only accounts have no usable local password hash. Email is used only for first-link policy and display; subsequent identity resolution uses the provider subject rather than a mutable address.

## Backend modules

Packages under `backend/src/main/java/com/gameio` are grouped by feature:

```text
achievement/   auth/          friend/        game/
gameresult/    leaderboard/   matchmaking/   multiplayer/
room/          user/          common/
```

The common shape is:

- controller/transport: HTTP or WebSocket translation only;
- application service: use cases and transaction boundaries;
- domain/engine: business invariants and state transitions;
- repository/store: PostgreSQL or Redis access;
- request/response records: external contract without exposing entities.

The `gameresult` module has two trusted ingestion paths. Single-player titles use server-issued seeded sessions and replay verification. Multiplayer engines create outcomes inside the authoritative coordinator and persist each match once through an idempotent match identifier.

## Realtime boundary

`GameWebSocketHandler` handles one transport envelope and dispatches game-neutral commands. `RoomService` validates room membership and lifecycle. `RealtimeGameCoordinator` owns active matches, and `EngineRegistry` resolves the engine for the catalog slug.

```text
Client command
  -> JWT/Origin handshake identity
  -> envelope and rate validation
  -> room membership validation
  -> authoritative engine input
  -> snapshot broadcast
  -> terminal outcome persistence
  -> EXP/achievement updates
  -> GAME_OVER broadcast
```

Clients send `PLACE_PIECE`, direction, stop or shoot input. They cannot submit position, HP, score or another player's identity. The server enforces 120 messages and 60 game inputs per user plus 240 messages per source IP in a one-second window, 60 handshakes per source IP and 20 per resolved user per minute, a 60-second reconnect grace period, a five-minute idle match limit and a 30-minute maximum match duration.

Room metadata survives in Redis, but an active engine does not survive a backend restart. A reconnect to such a stale `PLAYING` room produces `ROOM_EXPIRED`; the room is removed and the frontend exits the canvas. This limitation is why production stays on one non-sleeping replica.

## Frontend boundaries

Route screens live in `frontend/src/app` and feature code under `frontend/src/features`. Game implementations are isolated under `frontend/src/games`:

```text
games/
|-- core/          registry, runtime and shared artwork mapping
|-- game2048/      React rules/rendering
|-- snake/         Phaser scene and controls
|-- flappy-bird/   fixed-tick rules and React Canvas rendering
|-- tictactoe/     authoritative snapshot UI
|-- caro/          authoritative snapshot UI
`-- tank/          Phaser snapshot renderer and input mapping
```

The registry entry declares the slug, runtime and control profile. Route pages never contain a game's rules. The centralized API client owns refresh/retry behavior; `GameSocketClient` owns connection, reconnect, room rejoin and typed envelopes for every multiplayer game.

## Future extraction

The platform boundary is the catalog slug plus the room/input/state/result protocol. A later Node.js/Colyseus or horizontally scaled game service can replace the in-process coordinator while the Spring application continues to own accounts, catalog, friends and progression. Extraction is intentionally deferred until active-state durability and traffic justify the operational cost.
