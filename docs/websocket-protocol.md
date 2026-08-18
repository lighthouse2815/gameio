# WebSocket protocol

Gameio exposes one raw Spring WebSocket endpoint at `/ws`. The shared `GameSocketClient` uses it for lobby notifications, room lifecycle, Tic Tac Toe, Caro, Tank Battle and Type Rush.

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
| `ROOM_SPECTATE` | active public `roomId` UUID | Bind a read-only room channel; correlated `ROOM_STATE` and current `GAME_START` snapshots follow |
| `ROOM_UNSUBSCRIBE` | spectated `roomId` UUID | Unbind only the spectator channel and emit correlated `ROOM_LEFT`; room membership is unchanged |
| `ROOM_LEAVE` | `roomId` UUID | Leave/mark disconnected, clear this user's bindings and emit correlated `ROOM_LEFT` |
| `ROOM_READY` | `roomId` UUID | Mark the authenticated player ready; correlated `ROOM_STATE` follows |
| `ROOM_START` | `roomId` UUID | Owner explicitly starts when requirements are met; correlated `ROOM_STATE` follows |
| `ROOM_REMATCH` | finished `roomId` UUID | Return the same members to a fresh waiting state and bind the requesting socket |
| `GAME_INVITE_SEND` | `roomId`, `payload.recipientUsername` | Send a 60-second one-use invite to an online accepted friend |
| `GAME_INVITE_ACCEPT` | `payload.inviteId` | Revalidate and consume the invite, join the normal room path and bind this socket |
| `GAME_INVITE_DECLINE` | `payload.inviteId` | Consume the invite without joining and notify the sender |
| `MATCHMAKING_JOIN` | `payload.gameId` UUID | Enter the game's queue; correlated `MATCHMAKING_STATE` follows |
| `MATCHMAKING_LEAVE` | none | Remove the authenticated player's current queue ticket |
| `GAME_INPUT` | `roomId` and game-specific payload | Validate and apply an input to the active authoritative engine |
| `ROOM_REACTION` | bound/spectated `roomId`, `payload.reaction` | Broadcast one fixed value: `GG`, `NICE`, `WOW` or `REMATCH` |

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

### Type Rush input

```json
{
  "action": "TYPE_CHARACTER",
  "character": "a",
  "sequence": 17
}
```

Type Rush accepts exactly one NFC-normalized Unicode code point per command. The sequence starts at `0` for each player and must increase by exactly one. The client never sends passage text, progress, WPM, accuracy, elapsed time, winner or score. The authoritative snapshot contains the shared server-selected passage, a three-second `startsAt` countdown, a 90-second `deadline`, and both players' server-derived progress. Input before the countdown or after terminal state is rejected. The first player to complete the passage wins; on timeout, higher progress wins, then fewer errors, otherwise the result is a draw.

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
| `GAME_INVITE` | Incoming `{inviteId,roomId,roomCode,gameId,gameSlug,gameName,senderUsername,recipientId,expiresAt}` |
| `GAME_INVITE_SENT` | Correlated confirmation that Redis stored and delivered the invite |
| `GAME_INVITE_ACCEPTED` | `{inviteId,username,room}` for both sender and recipient |
| `GAME_INVITE_DECLINED` | `{inviteId,username}` for both sender and recipient |
| `OPPONENT_DISCONNECTED` | `{userId,reconnectGraceSeconds}` |
| `ROOM_REACTION` | Authenticated `{userId,username,reaction}` quick reaction; no free-form chat text |
| `ERROR` | Stable `{code,message}` plus the originating request ID when available |

There is no `PRESENCE_STATE` or heartbeat message in the current protocol. Friend presence is read through REST; the WebSocket container has no application idle timeout. Clients still reconnect on any network or intermediary close.

## Friend invitations

The sender must be a member of the named `WAITING` room and the recipient must be an accepted, online friend who is not already in that room. The backend derives both identities from authenticated state, checks room capacity, stores a random one-use invite in Redis for 60 seconds and delivers `GAME_INVITE` to every active recipient socket.

Acceptance atomically consumes the invite, revalidates friendship and room state, then delegates to the normal room join and socket-binding path. A stale invite cannot grant membership: it fails when its TTL elapsed or when the room started, filled or disappeared, the sender left, or the friendship was removed. Decline also consumes the token and notifies both sides.

After `GAME_OVER`, any existing member may send `ROOM_REMATCH`. The same room and member list return to `WAITING`, ready flags are cleared and each player reconnects their socket before the owner starts the next match. A fresh authoritative match identifier is created for every rematch.

## Spectators and reactions

Only a non-private room in `PLAYING` state can be spectated. A player already in that match must use `ROOM_JOIN`; a user with another active player-room binding must leave it before spectating. `ROOM_SPECTATE` adds the socket to broadcasts without changing the room's player list, ready state, capacity, presence game or disconnect/forfeit lifecycle. Spectator sockets fail the stricter binding check used by ready/start/rematch and every `GAME_INPUT`.

Reactions are deliberately not chat. The payload is an enum allowlist and the server supplies the user identity from the authenticated socket. Players and spectators sharing the channel may send at most four reactions per five-second window. `ROOM_UNSUBSCRIBE` and closing a spectator tab never call the room leave path.

## Limits and lifecycle

- The container accepts text frames up to 65,536 bytes, while the application rejects envelopes over 16,384 bytes; binary messages are disabled.
- Per user: at most 120 messages and 60 `GAME_INPUT` commands in a one-second window.
- Per user: at most four `ROOM_REACTION` commands in a five-second window, independent of game-input quota.
- Per source IP: at most 240 messages per second and 60 WebSocket handshakes per minute; after identity lookup, each user is limited to 20 handshakes per minute.
- Access-token expiry is checked on each message and by a scheduled five-second sweep.
- Match reconnect grace: 60 seconds.
- No-input match timeout: five minutes.
- Absolute match timeout: 30 minutes.

After the reconnect grace expires, the remaining player wins by forfeit when exactly one player remains; if nobody remains the result is a draw. Idle/maximum-duration expiry ends the match as a draw. The coordinator persists outcomes once, updates EXP/achievements and broadcasts `GAME_OVER` only after persistence succeeds.

## Reconnect and restart behavior

The browser reconnects with capped exponential backoff, refreshes the access token, then re-sends `ROOM_JOIN` for its active room. Redis retains room metadata/membership and the latest exact engine checkpoint. The coordinator sends the reconnecting player a fresh `GAME_STATE`; room commands and game inputs are rejected until that particular socket is bound. A user may bind several tabs to the same room, but must leave it before joining a different room.

Every changed authoritative state is checkpointed with the match ID, room metadata, activity/disconnect times and game-specific state. After a backend restart, the first player or spectator request validates the checkpoint against the Redis `PLAYING` room and reconstructs the engine. Players without a socket on the new process enter the normal 60-second reconnect grace; restored terminal state is finalized idempotently before another input is accepted.

If the checkpoint is missing, expired, unreadable, incompatible with the deployed engine or does not match the room/game/ordered players, the server deletes it and returns `ERROR` with code `ROOM_EXPIRED`. The UI clears the room/game snapshot and returns to a safe lobby state. Recovery is restart-safe on one replica; it is not distributed match ownership and does not permit multiple realtime backend replicas.

## Authoritative result flow

```text
validated input -> engine transition -> GAME_STATE broadcast
                -> terminal outcome -> idempotent PostgreSQL results
                -> statistics/EXP/achievements -> leaderboard cache generation
                -> seasonal Elo -> tournament bracket advancement (when linked)
                -> GAME_OVER broadcast
```

The production-capable `scripts/realtime-smoke.ps1` exercises this contract with two independent accounts and sockets. Its default path persists a Tic Tac Toe win/loss pair; `-GameSlug typing-race` completes the shared passage through genuine sequenced character inputs and verifies the persisted Type Rush win/loss pair.
