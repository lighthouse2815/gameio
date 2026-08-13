"use client";

import dynamic from "next/dynamic";
import type { ComponentType } from "react";

const Game2048 = dynamic(() => import("@/games/game2048/game2048"), {
  ssr: false,
  loading: () => (
    <p className="font-telemetry p-8 text-[10px] text-[var(--muted)]">
      LOADING 2048 ENGINE ///
    </p>
  ),
});

const SnakeGame = dynamic(() => import("@/games/snake/snake-game"), {
  ssr: false,
  loading: () => (
    <p className="font-telemetry p-8 text-[10px] text-[var(--muted)]">
      LOADING PHASER ENGINE ///
    </p>
  ),
});

const FlappyBirdGame = dynamic(
  () => import("@/games/flappy-bird/flappy-bird-game"),
  {
    ssr: false,
    loading: () => (
      <p className="font-telemetry p-8 text-[10px] text-[var(--muted)]">
        LOADING FLIGHT ENGINE ///
      </p>
    ),
  },
);

export type RegisteredGame = {
  slug: string;
  engine: "react" | "phaser" | "server-multiplayer";
  controlProfile:
    | "swipe-keys"
    | "direction-pad"
    | "single-action"
    | "turn-grid"
    | "combat";
  component?: ComponentType;
};

export const GAME_REGISTRY: Readonly<Record<string, RegisteredGame>> = {
  "2048": {
    slug: "2048",
    engine: "react",
    controlProfile: "swipe-keys",
    component: Game2048,
  },
  snake: {
    slug: "snake",
    engine: "phaser",
    controlProfile: "direction-pad",
    component: SnakeGame,
  },
  "flappy-bird": {
    slug: "flappy-bird",
    engine: "react",
    controlProfile: "single-action",
    component: FlappyBirdGame,
  },
  "tic-tac-toe": {
    slug: "tic-tac-toe",
    engine: "server-multiplayer",
    controlProfile: "turn-grid",
  },
  caro: {
    slug: "caro",
    engine: "server-multiplayer",
    controlProfile: "turn-grid",
  },
  "tank-battle": {
    slug: "tank-battle",
    engine: "server-multiplayer",
    controlProfile: "combat",
  },
};

export function getRegisteredGame(slug: string) {
  return GAME_REGISTRY[slug];
}

export function isLocalGame(slug: string) {
  const engine = getRegisteredGame(slug)?.engine;
  return engine === "react" || engine === "phaser";
}
