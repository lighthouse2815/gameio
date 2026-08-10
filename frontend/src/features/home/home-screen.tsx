"use client";

import Link from "next/link";
import { ArrowRight, Radio, ShieldCheck } from "lucide-react";
import { buttonStyles } from "@/components/ui/button";
import { SectionHeading } from "@/components/ui/section-heading";
import {
  EmptyState,
  ErrorState,
  LoadingGrid,
  Skeleton,
} from "@/components/ui/states";
import { useSession } from "@/features/auth/hooks";
import { GameGrid } from "@/features/games/game-grid";
import { useGames, useRecentGames } from "@/features/games/hooks";
import { useGlobalLeaderboard } from "@/features/leaderboard/hooks";
import { LeaderboardTable } from "@/features/leaderboard/leaderboard-table";
import { getErrorMessage } from "@/lib/api/api-error";
import { getGameArtwork } from "@/games/core/artwork";

function CatalogFailure({
  message,
  retry,
}: {
  message: string;
  retry: () => void;
}) {
  return (
    <div className="border-x border-b border-[var(--line)] p-5">
      <ErrorState
        title="Catalog link unavailable"
        description={message}
        onAction={retry}
      />
    </div>
  );
}

export function HomeScreen() {
  const catalog = useGames({ page: 0, size: 18 });
  const session = useSession();
  const recent = useRecentGames(Boolean(session.data));
  const leaderboard = useGlobalLeaderboard(0, 5);
  const allGames = catalog.data?.content ?? [];
  const featured = allGames[0];
  const featuredArtwork = featured
    ? getGameArtwork(featured.slug, featured.thumbnailUrl)
    : null;
  const catalogSelection = allGames
    .map((game, index) => ({ game, index }))
    .sort(
      (left, right) =>
        right.game.playsCount - left.game.playsCount ||
        left.index - right.index,
    )
    .slice(0, 6)
    .map(({ game }) => game);
  const online = allGames
    .filter((game) => game.gameType !== "SINGLE_PLAYER")
    .sort(
      (left, right) =>
        right.onlinePlayers - left.onlinePlayers ||
        right.playsCount - left.playsCount,
    )
    .slice(0, 3);

  return (
    <>
      <section className="grid min-h-[640px] border-x border-b border-[var(--line)] lg:grid-cols-[minmax(0,1.5fr)_minmax(300px,0.5fr)]">
        <div className="relative flex min-h-[520px] flex-col justify-between overflow-hidden bg-[var(--surface)] p-5 sm:p-9 lg:p-12">
          <div className="flex items-start justify-between">
            <p className="font-telemetry text-[9px] text-[var(--accent)]">
              PLAY NETWORK / REV 1.0
            </p>
            <p className="font-telemetry text-right text-[8px] leading-4 text-[var(--muted)]">
              VN-SGN-01
              <br />
              WEB CHANNEL
            </p>
          </div>
          <div>
            <h1 className="macro-title max-w-5xl">
              Your next
              <br />
              <span className="text-[var(--accent)]">run</span> starts.
            </h1>
            <p className="mt-8 max-w-xl text-sm leading-6 text-[var(--muted)] sm:text-base sm:leading-7">
              Solo puzzles, live rooms, server-verified rankings. One compact
              game network built for fast sessions and honest competition.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link href="/games" className={buttonStyles("primary")}>
                Enter game index
                <ArrowRight size={14} aria-hidden="true" />
              </Link>
              <Link href="/multiplayer" className={buttonStyles("secondary")}>
                <Radio size={14} aria-hidden="true" />
                Find live match
              </Link>
            </div>
          </div>
          <div className="font-telemetry flex flex-wrap gap-x-8 gap-y-2 border-t border-[var(--line)] pt-4 text-[8px] text-[var(--muted)]">
            <span>[ KEYBOARD + TOUCH ]</span>
            <span>[ AUTHORITATIVE SERVER ]</span>
            <span>[ DARK / LIGHT ]</span>
          </div>
        </div>

        <aside className="flex flex-col border-t border-[var(--line)] bg-[var(--background)] lg:border-l lg:border-t-0">
          <div className="font-telemetry flex items-center justify-between border-b border-[var(--line)] p-4 text-[8px]">
            <span>[ FEATURED OPERATION ]</span>
            <span className="status-online">CATALOG LIVE</span>
          </div>
          {catalog.isLoading ? (
            <div className="flex flex-1 flex-col p-5">
              <Skeleton className="h-52 w-full" />
              <Skeleton className="mt-7 h-9 w-4/5" />
              <Skeleton className="mt-3 h-3 w-full" />
              <Skeleton className="mt-2 h-3 w-2/3" />
            </div>
          ) : null}
          {catalog.isError ? (
            <div className="flex flex-1 items-center p-5">
              <ErrorState
                title="No feature signal"
                description={getErrorMessage(catalog.error)}
                onAction={() => void catalog.refetch()}
                className="min-h-0 w-full"
              />
            </div>
          ) : null}
          {featured ? (
            <Link
              href={"/game/" + encodeURIComponent(featured.slug)}
              className="group flex flex-1 flex-col"
            >
              <div className="relative h-56 overflow-hidden border-b border-[var(--line)] bg-[var(--surface-strong)]">
                {featuredArtwork ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={featuredArtwork}
                    alt=""
                    className="h-full w-full object-cover grayscale transition-[filter,transform] duration-300 group-hover:scale-[1.02] group-hover:grayscale-0"
                  />
                ) : (
                  <span className="flex h-full items-end p-5 text-8xl font-black tracking-[-0.08em] text-[var(--line-strong)]">
                    01
                  </span>
                )}
                <span className="font-telemetry absolute left-3 top-3 bg-[var(--accent)] px-2 py-1 text-[8px] text-white">
                  {featured.category}
                </span>
              </div>
              <div className="flex flex-1 flex-col p-5">
                <p className="font-telemetry text-[8px] text-[var(--muted)]">
                  UNIT / {featured.slug.toUpperCase()}
                </p>
                <h2 className="mt-3 text-3xl font-black uppercase tracking-[-0.05em] group-hover:text-[var(--accent)]">
                  {featured.name}
                </h2>
                <p className="mt-3 line-clamp-3 text-xs leading-5 text-[var(--muted)]">
                  {featured.description}
                </p>
                <span className="font-telemetry mt-auto flex items-center justify-between pt-8 text-[9px]">
                  <span>
                    {featured.playsCount.toLocaleString()} PLAYS /{" "}
                    {featured.onlinePlayers.toLocaleString()} LIVE
                  </span>
                  <ArrowRight size={13} aria-hidden="true" />
                </span>
              </div>
            </Link>
          ) : null}
        </aside>
      </section>

      <div className="mt-20">
        <SectionHeading
          index="01"
          eyebrow="Verified play volume"
          title="Popular games"
          href="/games"
        />
        {catalog.isLoading ? (
          <div className="border-x border-b border-[var(--line)] p-5">
            <LoadingGrid />
          </div>
        ) : null}
        {catalog.isError ? (
          <CatalogFailure
            message={getErrorMessage(catalog.error)}
            retry={() => void catalog.refetch()}
          />
        ) : null}
        {catalog.data && !catalogSelection.length ? (
          <div className="border-x border-b border-[var(--line)] p-5">
            <EmptyState
              title="Catalog awaiting games"
              description="No game records are available from the backend."
            />
          </div>
        ) : null}
        {catalogSelection.length ? (
          <div className="border-x border-b border-[var(--line)]">
            <GameGrid games={catalogSelection} />
          </div>
        ) : null}
      </div>

      <div className="mt-20">
        <SectionHeading
          index="02"
          eyebrow="Player history"
          title="Continue playing"
          href={session.data ? "/profile/" + session.data.username : "/login"}
          actionLabel={session.data ? "View profile" : "Sign in"}
        />
        <div className="border-x border-b border-[var(--line)] p-5">
          {!session.data ? (
            <EmptyState
              title="Session not linked"
              description="Sign in to retrieve your recent games. Gameio does not invent local history for signed-out visitors."
            />
          ) : null}
          {session.data && recent.isLoading ? <LoadingGrid count={4} /> : null}
          {recent.isError ? (
            <ErrorState
              title="History link unavailable"
              description={getErrorMessage(recent.error)}
              onAction={() => void recent.refetch()}
            />
          ) : null}
          {recent.data && !recent.data.content.length ? (
            <EmptyState
              title="No completed runs"
              description="Play an operation and your server-recorded history will appear here."
            />
          ) : null}
          {recent.data?.content.length ? (
            <ul className="grid gap-px border border-[var(--line)] bg-[var(--line)] sm:grid-cols-2">
              {recent.data.content.map((run) => (
                <li className="bg-[var(--surface)] p-5" key={run.id}>
                  <p className="font-telemetry text-[8px] text-[var(--accent)]">
                    {run.result} / {new Date(run.playedAt).toLocaleDateString()}
                  </p>
                  <Link
                    href={"/game/" + encodeURIComponent(run.gameSlug)}
                    className="mt-3 block text-2xl font-black uppercase tracking-[-0.04em] hover:text-[var(--accent)]"
                  >
                    {run.gameName}
                  </Link>
                  <div className="font-telemetry mt-7 flex justify-between text-[8px] text-[var(--muted)]">
                    <span>{run.score.toLocaleString()} PTS</span>
                    <span>{run.durationSeconds} SEC</span>
                  </div>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      </div>

      <div className="mt-20">
        <SectionHeading
          index="03"
          eyebrow="Rooms and queues"
          title="Online operations"
          href="/multiplayer"
          actionLabel="Open lobby"
        />
        {catalog.isLoading ? (
          <div className="border-x border-b border-[var(--line)] p-5">
            <LoadingGrid count={3} />
          </div>
        ) : null}
        {catalog.data && !online.length ? (
          <div className="border-x border-b border-[var(--line)] p-5">
            <EmptyState
              title="No multiplayer games available"
              description="The live catalog currently has no multiplayer titles."
            />
          </div>
        ) : null}
        {online.length ? (
          <div className="border-x border-b border-[var(--line)]">
            <GameGrid games={online} />
          </div>
        ) : null}
      </div>

      <div className="mt-20 grid border-x border-t border-[var(--line)] lg:grid-cols-[1fr_360px]">
        <section className="border-b border-[var(--line)] bg-[var(--surface)] lg:border-b-0 lg:border-r">
          <SectionHeading
            index="04"
            eyebrow="Verified results"
            title="Top players"
            href="/leaderboard"
            actionLabel="Full rankings"
          />
          {leaderboard.isLoading ? (
            <div className="p-5">
              <Skeleton className="h-72" />
            </div>
          ) : null}
          {leaderboard.isError ? (
            <div className="p-5">
              <ErrorState
                title="Rank link unavailable"
                description={getErrorMessage(leaderboard.error)}
                onAction={() => void leaderboard.refetch()}
              />
            </div>
          ) : null}
          {leaderboard.data && !leaderboard.data.content.length ? (
            <div className="p-5">
              <EmptyState
                title="Awaiting verified scores"
                description="The leaderboard opens when the server accepts its first valid result."
              />
            </div>
          ) : null}
          {leaderboard.data?.content.length ? (
            <LeaderboardTable entries={leaderboard.data.content} compact />
          ) : null}
        </section>
        <aside className="flex flex-col justify-between bg-[var(--background)] p-6 sm:p-8">
          <ShieldCheck
            size={38}
            className="text-[var(--accent)]"
            aria-hidden="true"
          />
          <div className="my-20">
            <p className="font-telemetry text-[9px] text-[var(--muted)]">
              [ TRUST MODEL ]
            </p>
            <h2 className="mt-3 text-4xl font-black uppercase tracking-[-0.055em]">
              Server decides.
            </h2>
            <p className="mt-5 text-sm leading-6 text-[var(--muted)]">
              Multiplayer clients send actions, never authoritative positions,
              health, turns, or results.
            </p>
          </div>
          <p className="font-telemetry text-[8px] text-[var(--muted)]">
            POLICY / ZERO-TRUST-GAME-CLIENT
          </p>
        </aside>
      </div>
    </>
  );
}
