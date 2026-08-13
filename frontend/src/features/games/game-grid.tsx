import { GameCard } from "@/features/games/game-card";
import type { GameSummary } from "@/features/games/types";
import { cn } from "@/lib/cn";

export function GameGrid({
  games,
  className,
}: {
  games: GameSummary[];
  className?: string;
}) {
  return (
    <div
      className={cn(
        "grid gap-px bg-[var(--line)] sm:grid-cols-2 xl:grid-cols-3",
        className,
      )}
    >
      {games.map((game, index) => (
        <GameCard
          key={game.id || game.slug}
          game={game}
          index={index}
          priority={index < 3}
        />
      ))}
    </div>
  );
}
