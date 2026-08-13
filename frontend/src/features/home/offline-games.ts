import type { GameSummary } from "@/features/games/types";
import { isLocalGame } from "@/games/core/game-registry";

export function selectOfflineGames(games: GameSummary[], limit = 6) {
  return games
    .filter(
      (game) =>
        game.gameType === "SINGLE_PLAYER" && isLocalGame(game.slug),
    )
    .sort(
      (left, right) =>
        right.playsCount - left.playsCount ||
        left.name.localeCompare(right.name),
    )
    .slice(0, limit);
}
