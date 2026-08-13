# Gameio frontend

Next.js 16 App Router frontend for the Gameio play network. The source is
organized by feature, while playable engines live under src/games and are
resolved through the central Game Registry.

## Run

Copy .env.example to .env.local, then:

    npm install
    npm run dev

Production and verification commands:

    npm run lint
    npm run typecheck
    npm test
    npm run build
    npm run preview
    npm run deploy

NEXT_PUBLIC_API_URL points to the backend REST base and NEXT_PUBLIC_WS_URL points
to the realtime endpoint. NEXT_PUBLIC_GOOGLE_CLIENT_ID is the public Web client
ID used by Google Identity Services; leave it empty to render a controlled
unavailable state, or set it to the same value as backend GOOGLE_CLIENT_ID.

## UI source model

components.json configures the shadcn/ui local-source model. Components under
src/components/ui are application-owned source files rather than a runtime UI
package. Add Radix primitives only when a component needs their interaction and
accessibility behavior; the current controls use semantic browser primitives.

## Game Registry

src/games/core/game-registry.tsx is the only mapping between a catalog slug and
its browser engine. 2048 uses a deterministic React rule engine; Snake uses
Phaser. Server-authoritative multiplayer games route through the room lobby
instead of simulating trusted state in the browser.

To add a game, implement its isolated engine, add unit tests for pure rules, and
register its slug, engine type, control profile, and component in the registry.
