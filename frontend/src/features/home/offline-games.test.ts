import { describe, expect, it } from "vitest";
import type { GameSummary } from "@/features/games/types";
import { selectOfflineGames } from "@/features/home/offline-games";

function game(
  slug: string,
  overrides: Partial<GameSummary> = {},
): GameSummary {
  return {
    id: slug,
    name: slug,
    slug,
    description: slug + " description",
    category: "CASUAL",
    gameType: "SINGLE_PLAYER",
    minPlayers: 1,
    maxPlayers: 1,
    onlinePlayers: 0,
    playsCount: 0,
    createdAt: "2026-08-13T00:00:00Z",
    ...overrides,
  };
}

describe("offline game selection", () => {
  it("groups the registered local engines and excludes multiplayer games", () => {
    const games = [
      game("2048", { name: "2048", playsCount: 4 }),
      game("snake", { name: "Snake", playsCount: 12 }),
      game("flappy-bird", { name: "Flappy Bird", playsCount: 8 }),
      game("breakout", { name: "Breakout" }),
      game("minesweeper", { name: "Minesweeper" }),
      game("memory-match", { name: "Memory Match" }),
      game("tic-tac-toe", {
        gameType: "TURN_BASED_MULTIPLAYER",
        minPlayers: 2,
        maxPlayers: 2,
      }),
      game("caro", {
        gameType: "TURN_BASED_MULTIPLAYER",
        minPlayers: 2,
        maxPlayers: 2,
      }),
      game("tank-battle", {
        gameType: "REALTIME_MULTIPLAYER",
        minPlayers: 2,
        maxPlayers: 4,
      }),
    ];

    expect(selectOfflineGames(games).map(({ slug }) => slug)).toEqual([
      "snake",
      "flappy-bird",
      "2048",
      "breakout",
      "memory-match",
      "minesweeper",
    ]);
  });

  it("does not advertise an unregistered solo catalog record as offline", () => {
    expect(selectOfflineGames([game("not-installed")])).toEqual([]);
  });

  it("sorts by verified plays and respects the homepage limit", () => {
    const games = [
      game("2048", { name: "2048", playsCount: 4 }),
      game("snake", { name: "Snake", playsCount: 12 }),
    ];

    expect(selectOfflineGames(games, 1).map(({ slug }) => slug)).toEqual([
      "snake",
    ]);
  });
});
