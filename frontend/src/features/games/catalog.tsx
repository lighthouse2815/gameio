"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { ChevronLeft, ChevronRight, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SelectField } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingGrid } from "@/components/ui/states";
import { GameGrid } from "@/features/games/game-grid";
import { useGames } from "@/features/games/hooks";
import type { GameType } from "@/features/games/types";
import { getErrorMessage } from "@/lib/api/api-error";

type CatalogProps = {
  initialQuery: string;
};

export function GamesCatalog({ initialQuery }: CatalogProps) {
  const router = useRouter();
  const [draftQuery, setDraftQuery] = useState(initialQuery);
  const [query, setQuery] = useState(initialQuery);
  const [category, setCategory] = useState("");
  const [gameType, setGameType] = useState<GameType | "">("");
  const [page, setPage] = useState(0);
  const games = useGames({
    q: query,
    category,
    gameType,
    page,
    size: 18,
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = draftQuery.trim();
    setQuery(normalized);
    setPage(0);
    router.replace(normalized ? "/games?q=" + encodeURIComponent(normalized) : "/games");
  }

  return (
    <section className="border-x border-b border-[var(--line)]">
      <form
        className="grid gap-px bg-[var(--line)] md:grid-cols-[minmax(240px,1fr)_220px_240px_auto]"
        onSubmit={submit}
      >
        <label className="bg-[var(--surface)] p-4">
          <span className="font-telemetry mb-2 block text-[9px] text-[var(--muted)]">
            Search index
          </span>
          <span className="grid grid-cols-[1fr_36px] border border-[var(--line-strong)] bg-[var(--background)]">
            <input
              type="search"
              value={draftQuery}
              onChange={(event) => setDraftQuery(event.target.value)}
              className="min-w-0 bg-transparent px-3 text-sm outline-none"
              placeholder="Title or call sign"
            />
            <Search size={15} className="self-center text-[var(--muted)]" aria-hidden="true" />
          </span>
        </label>
        <div className="bg-[var(--surface)] p-4">
          <SelectField
            label="Game mode"
            value={gameType}
            onChange={(event) => {
              setGameType(event.target.value as GameType | "");
              setPage(0);
            }}
          >
            <option value="">All modes</option>
            <option value="SINGLE_PLAYER">Single player</option>
            <option value="TURN_BASED_MULTIPLAYER">Turn-based PVP</option>
            <option value="REALTIME_MULTIPLAYER">Realtime PVP</option>
          </SelectField>
        </div>
        <div className="bg-[var(--surface)] p-4">
          <SelectField
            label="Classification"
            value={category}
            onChange={(event) => {
              setCategory(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All categories</option>
            <option value="CASUAL">Casual</option>
            <option value="PUZZLE">Puzzle</option>
            <option value="ACTION">Action</option>
            <option value="STRATEGY">Strategy</option>
            <option value="ARCADE">Arcade</option>
          </SelectField>
        </div>
        <div className="flex items-end bg-[var(--surface)] p-4">
          <Button className="w-full md:w-auto" type="submit">
            Apply filter
          </Button>
        </div>
      </form>

      <div className="p-4 sm:p-6">
        <div className="font-telemetry mb-4 flex flex-wrap items-center justify-between gap-3 text-[9px] text-[var(--muted)]">
          <span>
            [ RESULTS / {games.data?.totalElements ?? "—"} ]
          </span>
          {games.isFetching && !games.isLoading ? (
            <span className="text-[var(--accent)]">SYNCING ///</span>
          ) : null}
        </div>
        {games.isLoading ? <LoadingGrid /> : null}
        {games.isError ? (
          <ErrorState
            title="Game index unavailable"
            description={getErrorMessage(games.error)}
            onAction={() => void games.refetch()}
          />
        ) : null}
        {games.data && !games.data.content.length ? (
          <EmptyState
            title="No games match"
            description="Change the search term or classification. Results come directly from the backend catalog."
            actionLabel="Clear filters"
            onAction={() => {
              setDraftQuery("");
              setQuery("");
              setCategory("");
              setGameType("");
              setPage(0);
              router.replace("/games");
            }}
          />
        ) : null}
        {games.data?.content.length ? (
          <>
            <GameGrid games={games.data.content} />
            <div className="mt-5 flex items-center justify-between border border-[var(--line)] bg-[var(--surface)] p-3">
              <Button
                variant="ghost"
                compact
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                <ChevronLeft size={13} aria-hidden="true" />
                Previous
              </Button>
              <span className="font-telemetry text-[9px] text-[var(--muted)]">
                PAGE {page + 1} / {Math.max(1, games.data.totalPages)}
              </span>
              <Button
                variant="ghost"
                compact
                disabled={page + 1 >= games.data.totalPages}
                onClick={() => setPage((current) => current + 1)}
              >
                Next
                <ChevronRight size={13} aria-hidden="true" />
              </Button>
            </div>
          </>
        ) : null}
      </div>
    </section>
  );
}
