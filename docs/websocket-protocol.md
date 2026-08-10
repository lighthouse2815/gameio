# WebSocket protocol

Gameio exposes one raw Spring WebSocket endpoint at `/ws`. The shared `GameSocketClient` uses it for lobby notifications, room lifecycle, Tic Tac Toe, Caro and Tank Battle.

## Authenticated handshake

Credentials never appear in the WebSocket URL. The browser requests exactly one application protocol and one JWT-bearing protocol:

```javascript
new WebSocket("wss://api.example.com/ws", [
  "gameio.v1",
  "gameio.jwt." + accessToken,
]);
```

The server requires both values, decodes the access token with the configured issuer, verifies the user still exists and stores the identity/expiry on the session. It negotiates and echoes only `gameio.v1`; it never echoes the JWT-bearing protocol.

The handshake is rejected when the page `Origin` is not in `CORS_ALLOWED_ORIGINS`, either protocol is absent/duplicated, or the JWT is invalid/expired. An established connection is closed with policy-violation status when its access token expires. The client refreshes its access token before reconnecting.

## Client envelope

```json
{
  "type": "GAME_INPUT",
  "requestId": "8a75bb2fda574272b737a0f05a5d01ce",
  "roomId": "8d97d5df-41dd-43b4-a027-6cfba145d9bb",
  "payload": {
    "action": "PLACE_PIECE",
    "row": 0,
    "column": 2
  },
  "sentAt": "2026-08-10T12:00:00Z"
}
```

Fields:

- `type`: uppercase command name, 3-40 characters;
- `requestId`: client correlation/idempotency key, maximum 80 characters;
- `roomId`: room UUID for lifecycle/game commands (`ROOM_JOIN` also accepts a room code);
- `payload`: command-specific JSON;
- `sentAt`: client diagnostic timestamp; never used as authoritative game time.

Unknown payload fields fail deserialization. The authenticated principal always comes from the handshake, not from the envelope. Each socket remembers up to 256 recent non-empty request IDs and ignores a duplicate rather than applying the command twice.

## Client commands

| Type | Required data | Behavior |
| --- | --- | --- |
| `ROOM_JOIN` | `roomId` UUID or code | Join/reconnect and bind this socket; correlated `ROOM_STATE` follows |
| `ROOM_LEAVE` | `roomId` UUID | Leave/mark disconnected, clear this user's bindings and emit correlated `ROOM_LEFT` |
| `ROOM_READY` | `roomId` UUID | Mark the authenticated player ready; correlated `ROOM_STATE` follows |
| `ROOM_START` | `roomId` UUID | Owner explicitly starts when requirements are met; correlated `ROOM_STATE` follows |
| `MATCHMAKING_JOIN` | `payload.gameId` UUID | Enter the game's queue; correlated `MATCHMAKING_STATE` follows |
| `MATCHMAKING_LEAVE` | none | Remove the authenticated player's current queue ticket |
| `GAME_INPUT` | `roomId` and game-specific payload | Validate and apply an input to the active authoritative engine |

Ready does not auto-start a room. Only the owner can send a successful `ROOM_START`, and all required players must already be ready.

### Turn-based input

Tic Tac Toe uses coordinates `0..2`; Caro uses `0..14`:

```json
{
  "action": "PLACE_PIECE",
  "row": 4,
  "column": 7
}
```

The server checks membership, active turn, bounds, cell occupancy and terminal state before publishing a new board.

### Tank input

```json
{
  "action": "MOVE_RIGHT",
  "sequence": 184
}
```

Allowed actions are `MOVE_UP`, `MOVE_DOWN`, `MOVE_LEFT`, `MOVE_RIGHT`, `STOP` and `SHOOT`. `sequence` must increase for each player's input. The client never sends position, rotation, HP, bullet state, hit decisions or score.

## Server envelope

```json
{
  "type": "GAME_STATE",
  "requestId": "8a75bb2fda574272b737a0f05a5d01ce",
  "roomId": "8d97d5df-41dd-43b4-a027-6cfba145d9bb",
  "payload": {},
  "timestamp": "2026-08-10T12:00:00Z"
}
```

`timestamp` is server time. A correlated response/broadcast carries the originating `requestId`; asynchronous broadcasts use `null`.

## Server events

| Type | Payload / purpose |
| --- | --- |
| `CONNECTED` | Authenticated `{userId,username}` confirmation |
| `MATCHMAKING_STATE` | Current ticket: `QUEUED` or `MATCH_FOUND` |
| `MATCH_FOUND` | Room snapshot assigned to a matched player |
| `ROOM_STATE` | Full room metadata and player ready/connected state |
| `ROOM_LEFT` | `{userId}` confirmation sent to every socket for the leaving user |
| `GAME_START` | `{matchId,gameId,gameSlug,players,startedAt,state}` |
| `GAME_STATE` | Game-specific authoritative snapshot with a monotonic engine sequence |
| `GAME_OVER` | `{matchId,finalState,progression}` after durable result persistence |
| `OPPONENT_DISCONNECTED` | `{userId,reconnectGraceSeconds}` |
| `ERROR` | Stable `{code,message}` plus the originating request ID when available |

There is no `PRESENCE_STATE` or heartbeat message in the current protocol. Friend presence is read through REST; the WebSocket container has no application idle timeout. Clients still reconnect on any network or intermediary close.

## Reserved friend invite contract (not implemented)

The current backend and UI do not send game invitations. The friends screen may open a same-game lobby, but it never reports that a remote invite was delivered.

A future implementation reserves this boundary:

- client command `GAME_INVITE_SEND` with `{roomId,recipientUsername}`;
- server event `GAME_INVITE` with `{inviteId,roomId,roomCode,gameId,gameSlug,senderUsername,expiresAt}`;
- acceptance through the existing authenticated `ROOM_JOIN` path rather than a second membership mechanism;
- optional `GAME_INVITE_EXPIRED`/rejection acknowledgement keyed by `inviteId`.

Before delivery, the server must derive the sender from the socket JWT, verify an accepted friendship, verify that the sender belongs to the named `WAITING` room, ensure the room has capacity and mint a short-lived one-use invite. The recipient must be the authenticated recipient on delivery/acceptance. Invites expire when their TTL elapses, the room starts/fills/is deleted, the sender leaves or the friendship is removed. Redis is the appropriate ephemeral store; no invite may grant membership without the normal room checks.

These event names are a documented extension point only. They are deliberately absent from the current client/server event unions and handler switch until persistence, validation, UI and tests are delivered together.

## Limits and lifecycle

- The container accepts text frames up to 65,536 bytes, while the application rejects envelopes over 16,384 bytes; binary messages are disabled.
- Per user: at most 120 messages and 60 `GAME_INPUT` commands in a one-second window.
- Per source IP: at most 240 messages per second and 60 WebSocket handshakes per minute; after identity lookup, each user is limited to 20 handshakes per minute.
- Access-token expiry is checked on each message and by a scheduled five-second sweep.
- Match reconnect grace: 60 seconds.
- No-input match timeout: five minutes.
- Absolute match timeout: 30 minutes.

After the reconnect grace expires, the remaining player wins by forfeit when exactly one player remains; if nobody remains the result is a draw. Idle/maximum-duration expiry ends the match as a draw. The coordinator persists outcomes once, updates EXP/achievements and broadcasts `GAME_OVER` only after persistence succeeds.

## Reconnect and restart behavior

The browser reconnects with capped exponential backoff, refreshes the access token, then re-sends `ROOM_JOIN` for its active room. Redis retains room metadata/membership, and the running coordinator sends the reconnecting player a fresh `GAME_STATE`. Room commands and game inputs are rejected until that particular socket is bound. A user may bind several tabs to the same room, but must leave it before joining a different room.

Active engine state exists only inside the single Spring process. After a backend restart, a Redis room may still say `PLAYING` while its engine no longer exists. The server deletes that stale room and returns `ERROR` with code `ROOM_EXPIRED`; the UI clears the room/game snapshot and returns to a safe lobby state.

## Authoritative result flow

```text
validated input -> engine transition -> GAME_STATE broadcast
                -> terminal outcome -> idempotent PostgreSQL results
                -> statistics/EXP/achievements -> leaderboard cache generation
                -> GAME_OVER broadcast
```

The production-capable `scripts/realtime-smoke.ps1` exercises this contract with two independent accounts and sockets, including a persisted Tic Tac Toe win/loss pair.
