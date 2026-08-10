"use client";

import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SelectField } from "@/components/ui/field";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useGames } from "@/features/games/hooks";
import {
  useGameLeaderboard,
  useGlobalLeaderboard,
} from "@/features/leaderboard/hooks";
import { LeaderboardTable } from "@/features/leaderboard/leaderboard-table";
import { getErrorMessage } from "@/lib/api/api-error";

export function LeaderboardScreen() {
  const [gameId, setGameId] = useState("");
  const [page, setPage] = useState(0);
  const games = useGames({ page: 0, size: 100 });
  const global = useGlobalLeaderboard(page, 25);
  const perGame = useGameLeaderboard(gameId || undefined, page, 25);
  const ranking = gameId ? perGame : global;

  return (
    <section className="border-x border-b border-[var(--line)] p-4 sm:p-7">
      <div className="mb-5 grid items-end gap-4 border border-[var(--line)] bg-[var(--surface)] p-4 md:grid-cols-[1fr_auto]">
        <SelectField
          label="Ranking channel"
          value={gameId}
          onChange={(event) => {
            setGameId(event.target.value);
            setPage(0);
          }}
          disabled={games.isLoading}
        >
          <option value="">Global operations</option>
          {games.data?.content.map((game) => (
            <option value={game.id} key={game.id}>
              {game.name}
            </option>
          ))}
        </SelectField>
        <p className="font-telemetry pb-3 text-[8px] text-[var(--muted)]">
          REFRESH WINDOW / 30 SEC
        </p>
      </div>

      {ranking.isLoading ? (
        <div className="grid gap-px bg-[var(--line)] border border-[var(--line)]">
          {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={index} className="h-16" />
          ))}
        </div>
      ) : null}
      {ranking.isError ? (
        <ErrorState
          title="Rank channel offline"
          description={getErrorMessage(ranking.error)}
          onAction={() => void ranking.refetch()}
        />
      ) : null}
      {ranking.data && !ranking.data.content.length ? (
        <EmptyState
          title="No verified scores"
          description="This channel has not received a validated game result yet."
        />
      ) : null}
      {ranking.data?.content.length ? (
        <div className="border border-[var(--line)] bg-[var(--surface)]">
          <LeaderboardTable entries={ranking.data.content} />
          <div className="flex items-center justify-between border-t border-[var(--line)] p-3">
            <Button
              compact
              variant="ghost"
              disabled={page === 0}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
            >
              <ChevronLeft size={13} aria-hidden="true" />
              Previous
            </Button>
            <span className="font-telemetry text-[9px] text-[var(--muted)]">
              PAGE {page + 1} / {Math.max(1, ranking.data.totalPages)}
            </span>
            <Button
              compact
              variant="ghost"
              disabled={page + 1 >= ranking.data.totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              Next
              <ChevronRight size={13} aria-hidden="true" />
            </Button>
          </div>
        </div>
      ) : null}
    </section>
  );
}
