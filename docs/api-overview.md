# REST API overview

All backend routes below are rooted at `/api`. In production the browser calls the same paths on the Cloudflare frontend origin; the fixed-origin BFF forwards them to Spring Boot. Direct backend callers use `https://<backend-host>/api/...`.

Protected endpoints require `Authorization: Bearer <access-token>`. The server derives the player from that JWT and never trusts a request-body `userId`.

Every auth `POST` also requires:

```http
X-Gameio-CSRF: 1
```

## Error contract

Validation, domain and security errors use JSON with a stable machine code:

```json
{
  "timestamp": "2026-08-10T12:00:00Z",
  "status": 400,
  "code": "ROOM_NOT_READY",
  "message": "All required players must be ready",
  "path": "/api/rooms/8d97d5df-41dd-43b4-a027-6cfba145d9bb/start"
}
```

Bean Validation failures also include a `fieldErrors` object. Unexpected production failures return the generic `INTERNAL_ERROR` message and do not expose a stack trace.

## Authentication

| Method | Route | Auth | Body / result |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | Public | `{username,email,password}`; `201`, access token/user JSON and refresh cookie |
| `POST` | `/auth/login` | Public | `{login,password}` where login is username or email; access token/user JSON and refresh cookie |
| `POST` | `/auth/google` | Public | `{idToken}` from Google Identity Services; signs in or creates the linked player and returns the normal access token/user JSON plus refresh cookie |
| `POST` | `/auth/refresh` | Refresh cookie | Rotates the one-time refresh token and returns a new access token |
| `POST` | `/auth/logout` | Refresh cookie when present | Revokes the active family and expires the cookie; `204` |

Passwords must be 10-72 characters and are stored with BCrypt, never plaintext. The JSON auth response contains `tokenType`, `accessToken`, `accessExpiresAt` and `user`; it never contains the raw refresh token.

Google authentication uses the browser's Google Identity Services button rather than an OAuth redirect handled by Gameio. The backend accepts only the returned ID token, verifies its Google signature, issuer, expiry, configured web-client audience and verified-email claim, then resolves the durable Google subject mapping. A first-time Google identity creates a provider-only player; later requests for the same immutable subject use the same account even if Google's email claim changes. No unauthenticated email-based linking is allowed: any email already owned by a Gameio account returns `GOOGLE_ACCOUNT_LINK_REQUIRED`. A future linking flow must first authenticate the existing Gameio account so a pre-registered email cannot turn into a shared or hijacked account.

## Users and profile

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/users/me` | Bearer | Current account, including email and progression |
| `PATCH` | `/users/me` | Bearer | Update `{avatarUrl}`; non-null values must be HTTPS |
| `GET` | `/users/{username}` | Public | Public profile, stats and unlocked achievements |

Private email is returned only by the authenticated `/users/me` response, not by the public profile response.

## Catalog

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/games` | Public | Search the enabled catalog |
| `GET` | `/games/{slug}` | Public | Read one enabled game by slug |

`GET /games` accepts:

- `search` (up to 100 characters);
- `category`: `CASUAL`, `PUZZLE`, `ACTION`, `STRATEGY` or `ARCADE`;
- `gameType`: `SINGLE_PLAYER`, `TURN_BASED_MULTIPLAYER` or `REALTIME_MULTIPLAYER`;
- `page` and `size` (`size` maximum 100).

Each game response includes `onlinePlayers` from active Redis room metadata intersected with currently bound server sessions, and `playsCount` from durable PostgreSQL results. The frontend uses those values for online/popular surfaces; it does not invent ratings or player totals.

## Verified results and history

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/game-results/sessions` | Bearer | Start a server-verifiable single-player run with `{gameSlug}`; `201` |
| `POST` | `/game-results` | Bearer | Complete that session with `{sessionId,actions,durationSeconds}`; `201` |
| `GET` | `/game-results/me` | Bearer | Paginated current-player history |

The session response includes `sessionId`, `gameSlug`, a server seed, game-specific `initialState` and `expiresAt`. Completion replays the bounded action list on the server; there is no endpoint that accepts a client-asserted score. A completion response also includes the previous best, whether this is a new personal best, EXP/level and achievements unlocked by that run. Multiplayer results are written internally by the authoritative engine and share the same history table.

## Game preferences

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/game-preferences/me` | Bearer | List the current player's favorite/recent game records |
| `PUT` | `/game-preferences/{gameId}/favorite` | Bearer | Set `{favorite}` for an enabled game |
| `POST` | `/game-preferences/{gameId}/played` | Bearer | Update the server-owned recent-play timestamp; `201` |

The browser also keeps a bounded local preference copy so favorites and recent games work before login and offline. On login it merges newer local selections into the authenticated account. These records are convenience metadata only and never create scores, EXP or ranking entries.

## Player analytics

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/stats/me` | Bearer | All-time summary, per-game breakdown, 30-day activity, two-week score trend and achievement completion |

Analytics are derived only from durable `game_results` accepted by replay verification or an authoritative multiplayer engine. Dates and play streaks use the `Asia/Ho_Chi_Minh` business timezone. Empty accounts receive a stable zero-valued response and a 30-day zero-filled activity series rather than fabricated activity.

## Leaderboards and achievements

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/leaderboards` | Public | Paginated global rank by progression/wins |
| `GET` | `/games/{gameId}/leaderboard` | Public | Paginated rank for a game UUID |
| `GET` | `/achievements` | Public | Achievement catalog |
| `GET` | `/achievements/me` | Bearer | Current player's unlocked achievements |

Leaderboard entries contain `rank`, `userId`, `username`, `avatarUrl`, `score` and `wins`.

Leaderboard responses are cached in Redis for 30 seconds by scope/page/size. The durable query remains PostgreSQL; single-player and multiplayer result commits invalidate the global and affected-game cache generations after the transaction commits, and cache failure falls back to the database.

## Competitive seasons and tournaments

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/competition/season` | Public | Current annual season and its UTC start/end instants |
| `GET` | `/competition/ratings?gameId=...` | Public | Paginated game-specific Elo ladder for the current season |
| `GET` | `/competition/ratings/me` | Bearer | Current player's ratings across multiplayer games |
| `GET` | `/competition/tournaments` | Public | Paginated tournament directory |
| `GET` | `/competition/tournaments/{id}` | Public | Entrants, seeds and complete round/match bracket |
| `POST` | `/competition/tournaments` | Bearer | Create `{name,gameId,maxPlayers}` and enter as seed 1; `201` |
| `POST` | `/competition/tournaments/{id}/join` | Bearer | Idempotently reserve the next seed while registration is open |
| `POST` | `/competition/tournaments/{id}/start` | Bearer/creator | Close registration and allocate the first private match round |

Every authoritative multiplayer outcome updates a rating scoped to `(season, player, game)`. New ratings start at 1000. All participants are calculated from the same pre-match snapshot with K=32, so a two-player equal-rating win produces `+16/-16`; a draw uses an actual score of `0.5`. The `GAME_OVER.progression` payload includes `ratingBefore`, `ratingAfter` and `ratingDelta` for each player.

Tournaments are single elimination. The creator may start with any count from two through the configured 4/8/16 capacity; an odd player receives a recorded bye. Active pairings use normal private authoritative rooms. A win advances the winner, a draw allocates a fresh rematch room for the same pairing, and the final winner completes the durable tournament record.

## Daily Challenge

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/daily-challenges/today` | Public | Today's rotating solo game and Vietnam business-day window |
| `POST` | `/daily-challenges/today/sessions` | Bearer | Create a replay-verified session with the shared daily seed |
| `GET` | `/daily-challenges/me` | Bearer | Today's best, completed days, current/longest streak and distinct solo engines |
| `GET` | `/daily-challenges/{date}/leaderboard` | Public | Best verified score per player for that challenge date |

The business day is `Asia/Ho_Chi_Minh`. A session expires at the earlier of 24 hours after creation or the next local midnight. Challenge results must be completed through the normal `/game-results` replay-verification endpoint; ordinary offline runs are never promoted into a daily ranking.

## Friends

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/friends` | Bearer | Accepted friends with online/current-game presence |
| `GET` | `/friends/requests` | Bearer | `{incoming,outgoing}` request lists |
| `POST` | `/friends/requests` | Bearer | Send `{username}`; `201` |
| `POST` | `/friends/requests/{requestId}/accept` | Bearer | Accept an incoming UUID; `204` |
| `POST` | `/friends/requests/{requestId}/reject` | Bearer | Reject an incoming UUID; `204` |
| `DELETE` | `/friends/{username}` | Bearer | Remove an accepted friendship; `204` |

Friend pairs are canonical and case-insensitive, so the inverse duplicate relationship cannot be created.

Friend-to-game invitations use authenticated WebSocket commands rather than REST. They are one-use Redis records with a 60-second TTL and always join through the normal room validation path; see the [WebSocket protocol](websocket-protocol.md#friend-invitations).

## Rooms

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/rooms` | Bearer | List non-private rooms; filters: `gameId`, `status`, `page`, `size` |
| `POST` | `/rooms` | Bearer | Create `{gameId,maxPlayers,privateRoom}`; `201` |
| `POST` | `/rooms/join` | Bearer | Join with `{roomCode}` |
| `GET` | `/rooms/{roomId}` | Bearer/member | Read a room by UUID |
| `POST` | `/rooms/{roomId}/leave` | Bearer/member | Leave/disconnect; `204` |
| `POST` | `/rooms/{roomId}/ready` | Bearer/member | Mark ready and return room state |
| `POST` | `/rooms/{roomId}/start` | Bearer/owner | Explicitly start once required players are ready |

Same-room rematches use authenticated `ROOM_REMATCH` over WebSocket after `GAME_OVER`; the command resets ready state and returns the room to `WAITING`.

Room codes are six characters. Tic Tac Toe, Caro, Type Rush, Connect Four, Reversi and Rock Paper Scissors accept exactly two players; Tank Battle accepts two to four. The backend validates requested capacity against the selected game's catalog limits. Type Rush practice is local-only and does not create a room or verified result.

REST create/join is followed by WebSocket `ROOM_JOIN` so the live connection is bound to broadcasts. Realtime `ROOM_LEAVE` produces correlated `ROOM_LEFT` on every socket for that user. The lobby's REST exit explicitly disconnects/reconnects the shared socket; any other direct REST caller must likewise close or clear its live room channel because the REST response alone does not unbind a WebSocket.

## Matchmaking

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/matchmaking` | Bearer | Enter quick match with `{gameId}`; `202` |
| `GET` | `/matchmaking` | Bearer | Read the current `QUEUED` or `MATCH_FOUND` ticket |
| `DELETE` | `/matchmaking` | Bearer | Leave the queue; `204` |

When enough players queue for the same game, the server creates a private room and emits `MATCH_FOUND` to connected players. Ready and owner start remain explicit.

## Pagination

Paginated endpoints use zero-based `page` and bounded `size` parameters. Their stable envelope is:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Realtime commands, state snapshots and errors are specified separately in [WebSocket protocol](websocket-protocol.md).
