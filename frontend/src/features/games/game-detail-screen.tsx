"use client";

import Link from "next/link";
import {
  ArrowLeft,
  ArrowRight,
  Radio,
  ShieldCheck,
  Trophy,
} from "lucide-react";
import { buttonStyles } from "@/components/ui/button";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useAchievements, useGame } from "@/features/games/hooks";
import { relatedAchievementCodes } from "@/features/games/related-achievements";
import { useGameLeaderboard } from "@/features/leaderboard/hooks";
import { LeaderboardTable } from "@/features/leaderboard/leaderboard-table";
import { getGameArtwork } from "@/games/core/artwork";
import { GameRuntime } from "@/games/core/game-runtime";
import { getRegisteredGame } from "@/games/core/game-registry";
import { getErrorMessage, isApiError } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";

export function GameDetailScreen({
  slug,
  roomId,
  dailyChallenge,
}: {
  slug: string;
  roomId?: string;
  dailyChallenge?: boolean;
}) {
  const { t, formatDate, formatNumber } = useI18n();
  const game = useGame(slug);
  const achievements = useAchievements();
  const ranking = useGameLeaderboard(game.data?.id, 0, 8);

  if (game.isLoading) {
    return (
      <div className="border-x border-b border-[var(--line)] p-5 sm:p-8">
        <Skeleton className="h-[460px]" />
        <Skeleton className="mt-10 h-[620px]" />
      </div>
    );
  }
  if (game.isError) {
    return (
      <div className="border-x border-b border-[var(--line)] p-5 sm:p-8">
        <ErrorState
          title={t(isApiError(game.error) && game.error.status === 404 ? "Game not found" : "Game signal unavailable")}
          description={t(getErrorMessage(game.error))}
          actionLabel={t("Retry catalog link")}
          onAction={() => void game.refetch()}
        />
      </div>
    );
  }
  if (!game.data) return null;

  const detail = game.data;
  const artwork = getGameArtwork(detail.slug, detail.thumbnailUrl);
  const registration = getRegisteredGame(detail.slug);
  const local = registration && registration.engine !== "server-multiplayer";
  const relatedCodes = relatedAchievementCodes(
    detail.slug,
    detail.gameType !== "SINGLE_PLAYER",
  );
  const relatedAchievements =
    achievements.data?.filter((achievement) =>
      relatedCodes.has(achievement.code),
    ) ?? [];

  return (
    <>
      <section className="grid border-x border-b border-[var(--line)] bg-[var(--surface)] lg:grid-cols-[minmax(0,1.2fr)_minmax(360px,0.8fr)]">
        <div className="flex min-h-[520px] flex-col justify-between p-5 sm:p-9 lg:p-12">
          <div className="flex items-center justify-between gap-3">
            <Link
              href="/games"
              className="font-telemetry flex items-center gap-2 text-[9px] text-[var(--muted)] hover:text-[var(--accent)]"
            >
              <ArrowLeft size={13} aria-hidden="true" />
              {t("Game index")}
            </Link>
            <span className="font-telemetry text-[8px] text-[var(--accent)]">
              {t(detail.category)} / {t(detail.gameType)}
            </span>
          </div>
          <div>
            <p className="font-telemetry mb-5 text-[9px] text-[var(--muted)]">
              OPERATION / {detail.slug.toUpperCase()}
            </p>
            <h1 className="page-title">{t(detail.name)}</h1>
            <p className="mt-7 max-w-2xl text-sm leading-6 text-[var(--muted)] sm:text-base sm:leading-7">
              {t(detail.description)}
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              {local || roomId ? (
                <a href="#game-stage" className={buttonStyles("primary")}>
                  {t(local ? "Start local engine" : "Open active match")}
                  <ArrowRight size={14} aria-hidden="true" />
                </a>
              ) : (
                <Link
                  href={"/multiplayer?game=" + encodeURIComponent(detail.slug)}
                  className={buttonStyles("primary")}
                >
                  {t("Enter lobby")}
                  <ArrowRight size={14} aria-hidden="true" />
                </Link>
              )}
              <Link href="/leaderboard" className={buttonStyles("secondary")}>
                <Trophy size={14} aria-hidden="true" />
                {t("View ranks")}
              </Link>
            </div>
          </div>
          <dl className="grid grid-cols-2 gap-px border border-[var(--line)] bg-[var(--line)] sm:grid-cols-5">
            <div className="bg-[var(--background)] p-3">
              <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Players")}</dt>
              <dd className="mt-1 text-lg font-black">{detail.minPlayers}–{detail.maxPlayers}</dd>
            </div>
            <div className="bg-[var(--background)] p-3">
              <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Verified plays")}</dt>
              <dd className="mt-1 text-lg font-black">{formatNumber(detail.playsCount)}</dd>
            </div>
            <div className="bg-[var(--background)] p-3">
              <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Live players")}</dt>
              <dd className="mt-1 text-lg font-black">{formatNumber(detail.onlinePlayers)}</dd>
            </div>
            <div className="bg-[var(--background)] p-3">
              <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Cataloged")}</dt>
              <dd className="mt-1 text-sm font-black uppercase">
                {formatDate(detail.createdAt)}
              </dd>
            </div>
            <div className="bg-[var(--background)] p-3">
              <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Engine")}</dt>
              <dd className="mt-1 truncate text-sm font-black uppercase">{t(registration?.engine ?? "unregistered")}</dd>
            </div>
          </dl>
        </div>
        <div className="relative min-h-80 overflow-hidden border-t border-[var(--line)] bg-[var(--surface-strong)] lg:border-l lg:border-t-0">
          {artwork ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={artwork} alt={t("{game} artwork", { game: t(detail.name) })} className="h-full w-full object-cover" />
          ) : (
            <div className="grid h-full place-items-center">
              <span className="font-telemetry text-[9px] text-[var(--muted)]">{t("NO VISUAL SIGNAL")}</span>
            </div>
          )}
          <span className="font-telemetry absolute bottom-3 right-3 border border-white/30 bg-black px-2 py-1 text-[8px] text-white">
            ART / INTERNAL
          </span>
        </div>
      </section>

      <section id="game-stage" className="scroll-mt-24 border-x border-b border-[var(--line)] bg-[var(--surface)]">
        {dailyChallenge ? (
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--accent)] bg-[var(--background)] px-5 py-3">
            <p className="font-telemetry text-[9px] text-[var(--accent)]">{t("DAILY CHALLENGE / VERIFIED SHARED SEED")}</p>
            <Link href="/daily-challenge" className={buttonStyles("ghost")}>{t("Back to daily ranking")}</Link>
          </div>
        ) : null}
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--line)] px-5 py-4">
          <div>
            <p className="font-telemetry text-[8px] text-[var(--accent)]">{t("[ GAME STAGE ]")}</p>
            <h2 className="mt-1 text-xl font-black uppercase tracking-[-0.04em]">{t("Run interface")}</h2>
          </div>
          <p className="font-telemetry flex items-center gap-2 text-[8px] text-[var(--muted)]">
            {local ? <ShieldCheck size={13} aria-hidden="true" /> : <Radio size={13} aria-hidden="true" />}
            {t(local ? "LOCAL RULE ENGINE" : "SERVER ROOM REQUIRED")}
          </p>
        </header>
        <div className="p-3 sm:p-6 lg:p-8">
          <GameRuntime slug={detail.slug} roomId={roomId} />
        </div>
      </section>

      <section className="mt-20 border-x border-t border-[var(--line)] bg-[var(--surface)]">
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--line)] px-5 py-5">
          <div>
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              {t("[ VERIFIED OBJECTIVES ]")}
            </p>
            <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">
              {t("Related achievements")}
            </h2>
          </div>
          <ShieldCheck size={20} className="text-[var(--accent)]" aria-hidden="true" />
        </header>
        {achievements.isLoading ? <Skeleton className="m-5 h-40" /> : null}
        {achievements.isError ? (
          <div className="p-5">
            <ErrorState
              title={t("Achievement catalog unavailable")}
              description={t(getErrorMessage(achievements.error))}
              onAction={() => void achievements.refetch()}
            />
          </div>
        ) : null}
        {achievements.data && !relatedAchievements.length ? (
          <div className="p-5">
            <EmptyState
              title={t("No related objectives")}
              description={t("The backend achievement catalog has no objective mapped to this game.")}
            />
          </div>
        ) : null}
        {relatedAchievements.length ? (
          <ul className="grid gap-px bg-[var(--line)] md:grid-cols-2 xl:grid-cols-3">
            {relatedAchievements.map((achievement) => (
              <li className="bg-[var(--surface)] p-5" key={achievement.id}>
                <p className="font-telemetry text-[8px] text-[var(--accent)]">
                  {achievement.code} / +{achievement.expReward} EXP
                </p>
                <h3 className="mt-3 text-lg font-black uppercase tracking-[-0.035em]">
                  {t(achievement.name)}
                </h3>
                <p className="mt-3 text-xs leading-5 text-[var(--muted)]">
                  {t(achievement.description)}
                </p>
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      <section className="grid border-x border-t border-[var(--line)] lg:grid-cols-[1fr_360px]">
        <div className="min-w-0 border-b border-[var(--line)] bg-[var(--surface)] lg:border-b-0 lg:border-r">
          <header className="flex items-center justify-between border-b border-[var(--line)] px-5 py-5">
            <div>
              <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("[ SCORE CHANNEL ]")}</p>
              <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t("Operation ranks")}</h2>
            </div>
            <Trophy size={20} className="text-[var(--accent)]" aria-hidden="true" />
          </header>
          {ranking.isLoading ? <Skeleton className="m-5 h-72" /> : null}
          {ranking.isError ? (
            <div className="p-5">
              <ErrorState
                title={t("Ranking unavailable")}
                description={t(getErrorMessage(ranking.error))}
                onAction={() => void ranking.refetch()}
              />
            </div>
          ) : null}
          {ranking.data && !ranking.data.content.length ? (
            <div className="p-5">
              <EmptyState title={t("No validated scores")} description={t("The server has not accepted a result for this operation yet.")} />
            </div>
          ) : null}
          {ranking.data?.content.length ? <LeaderboardTable entries={ranking.data.content} compact /> : null}
        </div>
        <aside className="bg-[var(--background)] p-6">
          <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("[ RESULT AUTHORITY ]")}</p>
          <h2 className="mt-2 text-2xl font-black uppercase tracking-[-0.04em]">{t("Verified play")}</h2>
          <ShieldCheck size={24} className="mt-8 text-[var(--accent)]" aria-hidden="true" />
          <p className="mt-4 text-xs leading-5 text-[var(--muted)]">
            {t("Rankings are populated only from results accepted by the backend. Multiplayer clients transmit inputs while the server owns turns, positions, health, scoring, and progression.")}
          </p>
          <dl className="font-telemetry mt-8 grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]">
            <div className="flex justify-between bg-[var(--surface)] p-3">
              <dt>{t("Mode")}</dt>
              <dd>{t(detail.gameType)}</dd>
            </div>
            <div className="flex justify-between bg-[var(--surface)] p-3">
              <dt>{t("Capacity")}</dt>
              <dd>{detail.minPlayers}–{detail.maxPlayers}</dd>
            </div>
          </dl>
        </aside>
      </section>
    </>
  );
}
