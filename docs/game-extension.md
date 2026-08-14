# Adding a game

One lowercase catalog slug is the identity shared by PostgreSQL, the frontend registry and any backend replay verifier or multiplayer engine. Keep that slug stable after results exist.

## Choose the trust model first

| Game kind | Catalog `GameType` | Required server boundary |
| --- | --- | --- |
| Local game with no public result | `SINGLE_PLAYER` | Browser rules only; clearly label the score device-local |
| Ranked single-player game | `SINGLE_PLAYER` | Server-issued seed/session plus deterministic replay verifier |
| Turn-based online game | `TURN_BASED_MULTIPLAYER` | Authoritative engine validating player/turn/action |
| Tick-based online game | `REALTIME_MULTIPLAYER` | Authoritative engine owning time, movement, collisions and outcomes |

Never add a generic `{score: 99999}` completion endpoint. A result that affects history, EXP, achievements or a leaderboard must be reproduced or created by the backend.

## 1. Catalog and migration

Add a Flyway migration under `backend/src/main/resources/db/migration` rather than editing an applied migration. Insert the unique:

- name and slug;
- description and category;
- `GameType`;
- minimum/maximum players;
- enabled flag.

Add any achievement in the same forward-only migration and implement its backend rule in the central achievement service. Do not unlock achievements in frontend code.

## 2. Frontend registration

Create `frontend/src/games/<slug>/` and keep deterministic rules/input mapping independent of React or Phaser rendering. Add focused unit tests beside those rules.

Register the title in `frontend/src/games/core/game-registry.tsx` with:

- the exact catalog slug;
- `react`, `phaser` or `server-multiplayer` engine kind;
- a control profile;
- a lazy component for local titles.

For a multiplayer renderer, add its lazy mapping in `frontend/src/games/core/game-runtime.tsx`. Consume shared room/socket state; do not open another ad-hoc WebSocket from the game component.

Add optional artwork to `frontend/public/game-art/<slug>.webp` and map it in `frontend/src/games/core/artwork.ts`. Artwork must be a real optimized asset, not a broken URL or fake card field.

All keyboard games need appropriate touch controls, and every game needs real loading, failure, restart/exit and terminal states. Multiplayer renderers also need connecting, opponent wait/disconnect and reconnect/room-expired states.

## 3A. Ranked single-player verifier

Implement `GameReplayVerifier` under `backend/.../gameresult/replay/<slug>`:

```java
public interface GameReplayVerifier {
    String gameSlug();
    Object initialState(long seed);
    VerifiedReplay verify(long seed, List<String> actions);
}
```

Make the client and verifier use the same documented PRNG, board/grid coordinates, tick/action order, collision rules and terminal condition. The client starts with `POST /api/game-results/sessions`, uses the returned seed/initial state, records only bounded actions, and calls `POST /api/game-results` only when terminal.

The verifier must reject:

- unsupported or impossible actions;
- action lists beyond the endpoint bound;
- non-terminal submissions;
- duration shorter than the replay's minimum credible duration;
- any mismatch caused by client-authoritative state.

Spring auto-injects verifier implementations into `ReplayVerifierRegistry`; duplicate slugs fail startup.

Required tests include deterministic initial state, valid replay/score, invalid action, impossible reversal/turn where relevant, collision/terminal detection, action bound and minimum-duration enforcement. Also test the service path that persists the verified result and progression.

## 3B. Authoritative multiplayer engine

Implement `AuthoritativeEngine` and an `AuthoritativeEngineFactory` for the slug:

```java
public interface AuthoritativeEngine {
    Object snapshot();
    EngineUpdate input(UUID userId, GameInput input, Instant now);
    EngineUpdate tick(Instant now);
    boolean requiresServerTick();
}
```

The real interface supplies no-op defaults for the last two methods, so turn-based engines need only `snapshot` and `input`. The factory validates the player count and returns an isolated engine instance. Spring auto-registers factories in `EngineRegistry`; room/matchmaking/WebSocket transport remains shared.

The engine must:

1. verify that the authenticated player belongs to the engine;
2. accept only the smallest action DTO required for the game;
3. validate turn, coordinates, monotonic input and cooldowns as applicable;
4. own all authoritative timing/state;
5. emit immutable public snapshots with a monotonic sequence;
6. return terminal `EngineOutcome` values for every player exactly once.

Do not put a custom `userId` in the payload. Do not accept client positions, HP, hits, winners or scores. Extend the shared `GameInput` DTO only when the new field is a genuine input and add unknown-field rejection tests.

For a server-tick game, keep `tick` bounded and deterministic for a supplied `Instant`; never block the coordinator scheduler. Consider the current five-minute idle and 30-minute absolute match limits when designing the terminal rules.

## 4. API and UI integration

- Confirm catalog search/detail renders the backend record rather than duplicated page metadata.
- Add room-capacity controls that exactly match catalog min/max players.
- For multiplayer, navigate with `/game/<slug>?room=<room-uuid>` only after validated room membership.
- Reuse the implemented `GAME_INVITE_SEND`/`GAME_INVITE` validation and expiry contract; do not create a game-specific invitation path.
- Add result history/leaderboard presentation only when the backend trust path exists.
- Reuse the centralized API client and `GameSocketClient`; do not scatter `fetch` or socket construction inside the renderer.
- Add responsive desktop and touch controls and verify both themes.

## 5. Verification gate

Run the relevant focused tests first, then the complete gates:

```powershell
Set-Location backend
cmd /c .\mvnw.cmd -B -ntp clean verify

Set-Location ..\frontend
npm ci --no-audit --no-fund
npm run check
npm run cf:build

Set-Location ..
docker compose up --build -d
.\scripts\smoke.ps1
```

For multiplayer changes also run:

```powershell
.\scripts\realtime-smoke.ps1
```

Finally use separate browser sessions to exercise the real start/input/terminal/reconnect flow and inspect console/network output. Update the API/protocol/architecture docs in the same change when a contract changes.

A game is not complete merely because its card is visible or its renderer builds. Completion requires a working trust path, meaningful rule tests, full build success and real runtime interaction.
