"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { ChevronLeft, ChevronRight, Heart, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SelectField } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingGrid } from "@/components/ui/states";
import { GameGrid } from "@/features/games/game-grid";
import { useGames } from "@/features/games/hooks";
import type { GameType } from "@/features/games/types";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";
import { favoriteGameIds, useGamePreferencesStore } from "@/stores/game-preferences-store";

type CatalogProps = {
  initialQuery: string;
};

export function GamesCatalog({ initialQuery }: CatalogProps) {
  const { t } = useI18n();
  const router = useRouter();
  const [draftQuery, setDraftQuery] = useState(initialQuery);
  const [query, setQuery] = useState(initialQuery);
  const [category, setCategory] = useState("");
  const [gameType, setGameType] = useState<GameType | "">("");
  const [page, setPage] = useState(0);
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const preferenceRecords = useGamePreferencesStore((state) => state.records);
  const favorites = favoriteGameIds(preferenceRecords);
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
            {t("Search index")}
          </span>
          <span className="grid grid-cols-[1fr_36px] border border-[var(--line-strong)] bg-[var(--background)]">
            <input
              type="search"
              value={draftQuery}
              onChange={(event) => setDraftQuery(event.target.value)}
              className="min-w-0 bg-transparent px-3 text-sm outline-none"
              placeholder={t("Title or call sign")}
            />
            <Search size={15} className="self-center text-[var(--muted)]" aria-hidden="true" />
          </span>
        </label>
        <div className="bg-[var(--surface)] p-4">
          <SelectField
            label={t("Game mode")}
            value={gameType}
            onChange={(event) => {
              setGameType(event.target.value as GameType | "");
              setPage(0);
            }}
          >
            <option value="">{t("All modes")}</option>
            <option value="SINGLE_PLAYER">{t("Single player")}</option>
            <option value="TURN_BASED_MULTIPLAYER">{t("Turn-based PVP")}</option>
            <option value="REALTIME_MULTIPLAYER">{t("Realtime PVP")}</option>
          </SelectField>
        </div>
        <div className="bg-[var(--surface)] p-4">
          <SelectField
            label={t("Classification")}
            value={category}
            onChange={(event) => {
              setCategory(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t("All categories")}</option>
            <option value="CASUAL">{t("Casual")}</option>
            <option value="PUZZLE">{t("Puzzle")}</option>
            <option value="ACTION">{t("Action")}</option>
            <option value="STRATEGY">{t("Strategy")}</option>
            <option value="ARCADE">{t("Arcade")}</option>
          </SelectField>
        </div>
        <div className="flex items-end bg-[var(--surface)] p-4">
          <Button className="w-full md:w-auto" type="submit">
            {t("Apply filter")}
          </Button>
        </div>
      </form>

      <div className="p-4 sm:p-6">
        <div className="font-telemetry mb-4 flex flex-wrap items-center justify-between gap-3 text-[9px] text-[var(--muted)]">
          <span>
            [ {t("RESULTS")} / {games.data?.totalElements ?? "—"} ]
          </span>
          {games.isFetching && !games.isLoading ? (
            <span className="text-[var(--accent)]">{t("SYNCING ///")}</span>
          ) : null}
        </div>
        {games.isLoading ? <LoadingGrid /> : null}
        {games.isError ? (
          <ErrorState
            title={t("Game index unavailable")}
            description={t(getErrorMessage(games.error))}
            onAction={() => void games.refetch()}
          />
        ) : null}
        {games.data && !games.data.content.length ? (
          <EmptyState
            title={t("No games match")}
            description={t("Change the search term or classification. Results come directly from the backend catalog.")}
            actionLabel={t("Clear filters")}
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
            <button
              type="button"
              className={
                "font-telemetry mb-4 inline-flex min-h-10 items-center gap-2 border px-3 text-[9px] " +
                (favoritesOnly
                  ? "border-[var(--accent)] text-[var(--accent)]"
                  : "border-[var(--line)] text-[var(--muted)]")
              }
              aria-pressed={favoritesOnly}
              onClick={() => setFavoritesOnly((value) => !value)}
            >
              <Heart size={13} fill={favoritesOnly ? "currentColor" : "none"} aria-hidden="true" />
              {t("Favorites only")} / {favorites.size}
            </button>
            {favoritesOnly && !games.data.content.some((game) => favorites.has(game.id)) ? (
              <EmptyState
                title={t("No favorites on this page")}
                description={t("Disable the favorites filter or mark a game with the heart button.")}
              />
            ) : (
              <GameGrid games={favoritesOnly ? games.data.content.filter((game) => favorites.has(game.id)) : games.data.content} />
            )}
            <div className="mt-5 flex items-center justify-between border border-[var(--line)] bg-[var(--surface)] p-3">
              <Button
                variant="ghost"
                compact
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                <ChevronLeft size={13} aria-hidden="true" />
                {t("Previous")}
              </Button>
              <span className="font-telemetry text-[9px] text-[var(--muted)]">
                {t("PAGE")} {page + 1} / {Math.max(1, games.data.totalPages)}
              </span>
              <Button
                variant="ghost"
                compact
                disabled={page + 1 >= games.data.totalPages}
                onClick={() => setPage((current) => current + 1)}
              >
                {t("Next")}
                <ChevronRight size={13} aria-hidden="true" />
              </Button>
            </div>
          </>
        ) : null}
      </div>
    </section>
  );
}
