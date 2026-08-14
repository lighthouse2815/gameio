"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { CalendarCheck, Flame, ShieldCheck, Trophy } from "lucide-react";
import { buttonStyles } from "@/components/ui/button";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useSession } from "@/features/auth/hooks";
import { dailyChallengeApi } from "@/features/daily-challenge/api";
import { LeaderboardTable } from "@/features/leaderboard/leaderboard-table";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";
import { asPage } from "@/types/page";

function remainingLabel(endsAt: string, now: number) {
  const seconds = Math.max(0, Math.floor((Date.parse(endsAt) - now) / 1000));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  return [hours, minutes, rest].map((value) => String(value).padStart(2, "0")).join(":");
}

export function DailyChallengeScreen() {
  const { t, formatDate, formatNumber } = useI18n();
  const session = useSession();
  const [now, setNow] = useState(() => Date.now());
  const challenge = useQuery({
    queryKey: ["daily-challenge", "today"],
    queryFn: dailyChallengeApi.today,
  });
  const progress = useQuery({
    queryKey: ["daily-challenge", "progress"],
    queryFn: dailyChallengeApi.progress,
    enabled: Boolean(session.data),
  });
  const ranking = useQuery({
    queryKey: ["daily-challenge", "leaderboard", challenge.data?.date],
    queryFn: () => dailyChallengeApi.leaderboard(challenge.data?.date ?? ""),
    select: (response) => asPage(response, 0, 20),
    enabled: Boolean(challenge.data?.date),
  });

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);

  if (challenge.isLoading) {
    return <Skeleton className="h-[720px] border-x border-b border-[var(--line)]" />;
  }
  if (challenge.isError || !challenge.data) {
    return (
      <div className="border-x border-b border-[var(--line)] p-6">
        <ErrorState
          title={t("Daily Challenge unavailable")}
          description={t(getErrorMessage(challenge.error))}
          onAction={() => void challenge.refetch()}
        />
      </div>
    );
  }

  const daily = challenge.data;
  return (
    <div className="grid gap-0 border-x border-b border-[var(--line)] xl:grid-cols-[minmax(0,1fr)_380px]">
      <div className="min-w-0">
        <section className="border-b border-[var(--line)] bg-[var(--surface)] p-5 sm:p-8">
          <div className="flex flex-wrap items-start justify-between gap-5">
            <div>
              <p className="font-telemetry text-[9px] text-[var(--accent)]">
                {t("CHALLENGE DATE")} / {daily.date}
              </p>
              <h2 className="mt-4 text-4xl font-black uppercase tracking-[-0.055em] sm:text-6xl">
                {t(daily.gameName)}
              </h2>
              <p className="mt-5 max-w-2xl text-sm leading-6 text-[var(--muted)]">
                {t(daily.gameDescription)}
              </p>
            </div>
            <div className="border border-[var(--accent)] bg-[var(--background)] p-4 text-right">
              <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("RESETS IN")}</p>
              <p className="font-telemetry mt-2 text-xl font-black text-[var(--accent)]">
                {remainingLabel(daily.endsAt, now)}
              </p>
            </div>
          </div>
          <div className="mt-8 flex flex-wrap gap-3">
            {session.data ? (
              <Link
                href={
                  "/game/" +
                  encodeURIComponent(daily.gameSlug) +
                  "?challenge=today#game-stage"
                }
                className={buttonStyles("primary")}
              >
                <CalendarCheck size={14} aria-hidden="true" />
                {t(progress.data?.completedToday ? "Improve today's score" : "Start today's challenge")}
              </Link>
            ) : (
              <Link href="/login" className={buttonStyles("primary")}>
                {t("Sign in to enter")}
              </Link>
            )}
            <span className={buttonStyles("secondary")} aria-label={t("Server verified daily seed")}>
              <ShieldCheck size={14} aria-hidden="true" />
              {t("Same seed for everyone")}
            </span>
          </div>
        </section>

        <section className="bg-[var(--surface)]">
          <header className="flex items-center justify-between border-b border-[var(--line)] p-5">
            <div>
              <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("[ DAILY RANK CHANNEL ]")}</p>
              <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t("Today's leaderboard")}</h2>
            </div>
            <Trophy size={20} className="text-[var(--accent)]" aria-hidden="true" />
          </header>
          {ranking.isLoading ? <Skeleton className="m-5 h-72" /> : null}
          {ranking.isError ? (
            <div className="p-5">
              <ErrorState title={t("Daily ranking unavailable")} description={t(getErrorMessage(ranking.error))} onAction={() => void ranking.refetch()} />
            </div>
          ) : null}
          {ranking.data && !ranking.data.content.length ? (
            <div className="p-5">
              <EmptyState title={t("No daily results yet")} description={t("Complete the verified challenge to open today's ranking.")} />
            </div>
          ) : null}
          {ranking.data?.content.length ? <LeaderboardTable entries={ranking.data.content} /> : null}
        </section>
      </div>

      <aside className="border-t border-[var(--line)] bg-[var(--background)] p-5 xl:border-l xl:border-t-0">
        <Flame size={24} className="text-[var(--accent)]" aria-hidden="true" />
        <p className="font-telemetry mt-5 text-[8px] text-[var(--muted)]">{t("[ PLAYER STREAK ]")}</p>
        <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t("Daily progress")}</h2>
        {!session.data ? (
          <p className="mt-5 text-xs leading-5 text-[var(--muted)]">{t("Sign in to track streaks and personal best scores.")}</p>
        ) : progress.isLoading ? (
          <Skeleton className="mt-5 h-64" />
        ) : progress.isError ? (
          <ErrorState title={t("Progress unavailable")} description={t(getErrorMessage(progress.error))} onAction={() => void progress.refetch()} />
        ) : progress.data ? (
          <dl className="mt-5 grid gap-px border border-[var(--line)] bg-[var(--line)]">
            {[
              ["Current streak", progress.data.currentStreak],
              ["Longest streak", progress.data.longestStreak],
              ["Completed days", progress.data.completedDays],
              ["Solo engines cleared", progress.data.distinctSoloGames],
              ["Today's best", formatNumber(progress.data.todayBestScore)],
            ].map(([label, value]) => (
              <div className="flex items-center justify-between bg-[var(--surface)] p-4" key={String(label)}>
                <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t(String(label))}</dt>
                <dd className="text-lg font-black">{value}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        <p className="font-telemetry mt-5 text-[8px] leading-5 text-[var(--muted)]">
          {t("BUSINESS TIME")} / ASIA_HO_CHI_MINH<br />
          {formatDate(daily.startsAt)} — {formatDate(daily.endsAt)}
        </p>
      </aside>
    </div>
  );
}
