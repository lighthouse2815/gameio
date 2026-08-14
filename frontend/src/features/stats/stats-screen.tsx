"use client";

import Link from "next/link";
import { Activity, Award, Clock3, Flame, Gamepad2, Target, TrendingDown, TrendingUp, Trophy } from "lucide-react";
import { LoginRequired } from "@/components/auth/login-required";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useSession } from "@/features/auth/hooks";
import { usePlayerStats } from "@/features/stats/hooks";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";

function durationLabel(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

export function StatsScreen() {
  const { t, formatDate, formatNumber } = useI18n();
  const session = useSession();
  const stats = usePlayerStats(Boolean(session.data));

  if (session.isLoading) return <Skeleton className="h-[680px]" />;
  if (!session.data) return <LoginRequired title={t("Player analytics locked")} />;
  if (stats.isLoading) {
    return <div className="grid gap-4"><Skeleton className="h-40" /><Skeleton className="h-72" /><Skeleton className="h-96" /></div>;
  }
  if (stats.isError) {
    return <ErrorState title={t("Player analytics unavailable")} description={t(getErrorMessage(stats.error))} onAction={() => void stats.refetch()} />;
  }
  if (!stats.data) return null;

  const data = stats.data;
  const maximumDailyGames = Math.max(1, ...data.activity.map((day) => day.gamesPlayed));
  const trend = data.scoreTrend.percentChange;

  return (
    <div className="grid gap-8">
      <section className="grid gap-px border border-[var(--line)] bg-[var(--line)] sm:grid-cols-2 xl:grid-cols-6">
        {[
          { label: "Verified games", value: formatNumber(data.summary.gamesPlayed), icon: Gamepad2 },
          { label: "Competitive win rate", value: `${data.summary.winRate.toFixed(1)}%`, icon: Trophy },
          { label: "Total score", value: formatNumber(data.summary.totalScore), icon: Target },
          { label: "Play time", value: durationLabel(data.summary.totalDurationSeconds), icon: Clock3 },
          { label: "Active days / 30", value: formatNumber(data.summary.activeDays), icon: Activity },
          { label: "Current play streak", value: `${formatNumber(data.summary.currentPlayStreak)}d`, icon: Flame },
        ].map(({ label, value, icon: Icon }) => (
          <dl key={label} className="bg-[var(--surface)] p-4 sm:p-5">
            <dt className="font-telemetry flex items-center justify-between gap-2 text-[8px] text-[var(--muted)]">{t(label)}<Icon size={14} className="text-[var(--accent)]" aria-hidden="true" /></dt>
            <dd className="mt-5 text-2xl font-black uppercase tracking-[-0.04em]">{value}</dd>
          </dl>
        ))}
      </section>

      <section className="border border-[var(--line)] bg-[var(--surface)]">
        <header className="flex flex-wrap items-end justify-between gap-4 border-b border-[var(--line)] p-5">
          <div>
            <p className="font-telemetry text-[8px] text-[var(--accent)]">{t("[ 30-DAY ACTIVITY ]")}</p>
            <h2 className="mt-2 text-2xl font-black uppercase tracking-[-0.04em]">{t("Consistency map")}</h2>
          </div>
          <div className="text-right">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("7-day score trend")}</p>
            <p className={"mt-1 flex items-center justify-end gap-2 text-xl font-black " + (trend != null && trend < 0 ? "text-[var(--danger)]" : "text-[var(--online)]")}>
              {trend == null ? t("NEW BASELINE") : `${trend >= 0 ? "+" : ""}${trend.toFixed(1)}%`}
              {trend != null && trend < 0 ? <TrendingDown size={18} aria-hidden="true" /> : <TrendingUp size={18} aria-hidden="true" />}
            </p>
          </div>
        </header>
        <div className="overflow-x-auto p-5">
          <div className="grid min-w-[720px] grid-cols-[repeat(30,minmax(16px,1fr))] items-end gap-2" role="img" aria-label={t("Games played on each of the last 30 days")}>
            {data.activity.map((day) => (
              <div key={day.date} className="group grid h-44 content-end gap-2" title={`${formatDate(day.date)}: ${day.gamesPlayed} ${t("games")}`}>
                <span className="font-telemetry text-center text-[7px] text-[var(--muted)] opacity-0 group-hover:opacity-100">{day.gamesPlayed}</span>
                <span className="block min-h-1 bg-[var(--accent)] transition-[height]" style={{ height: `${Math.max(4, (day.gamesPlayed / maximumDailyGames) * 120)}px`, opacity: day.gamesPlayed ? 1 : 0.15 }} />
                <span className="font-telemetry truncate text-center text-[6px] text-[var(--muted)]">{day.date.slice(8)}</span>
              </div>
            ))}
          </div>
        </div>
        <dl className="grid gap-px border-t border-[var(--line)] bg-[var(--line)] sm:grid-cols-3">
          <div className="bg-[var(--background)] p-4"><dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Recent 7-day average")}</dt><dd className="mt-2 text-xl font-black">{formatNumber(Math.round(data.scoreTrend.recentSevenDayAverage))}</dd></div>
          <div className="bg-[var(--background)] p-4"><dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Previous 7-day average")}</dt><dd className="mt-2 text-xl font-black">{formatNumber(Math.round(data.scoreTrend.previousSevenDayAverage))}</dd></div>
          <div className="bg-[var(--background)] p-4"><dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Achievement completion")}</dt><dd className="mt-2 flex items-center gap-2 text-xl font-black"><Award size={17} className="text-[var(--accent)]" aria-hidden="true" />{data.achievements.unlocked}/{data.achievements.total} · {data.achievements.completionPercent.toFixed(0)}%</dd></div>
        </dl>
      </section>

      <section className="border border-[var(--line)] bg-[var(--surface)]">
        <header className="flex flex-wrap items-center justify-between gap-4 border-b border-[var(--line)] p-5">
          <div><p className="font-telemetry text-[8px] text-[var(--accent)]">{t("[ GAME BREAKDOWN ]")}</p><h2 className="mt-2 text-2xl font-black uppercase tracking-[-0.04em]">{t("Performance by engine")}</h2></div>
          <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("MOST PLAYED")} / {data.mostPlayedGameSlug ? t(data.mostPlayedGameSlug) : "—"}</p>
        </header>
        {!data.games.length ? <div className="p-5"><EmptyState title={t("No verified history")} description={t("Complete a ranked solo run or authoritative multiplayer match to populate analytics.")} /></div> : null}
        {data.games.length ? (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] border-collapse text-left">
              <thead className="font-telemetry text-[8px] text-[var(--muted)]"><tr>{["Game", "Runs", "Best", "Average", "W / L / D", "Win rate", "Time", "Last played"].map((label) => <th key={label} className="border-b border-[var(--line)] px-4 py-3 font-normal">{t(label)}</th>)}</tr></thead>
              <tbody>
                {data.games.map((game) => (
                  <tr key={game.gameId} className="border-b border-[var(--line)] last:border-b-0 hover:bg-[var(--background)]">
                    <td className="p-4"><Link href={`/game/${encodeURIComponent(game.gameSlug)}`} className="font-black uppercase hover:text-[var(--accent)]">{t(game.gameName)}</Link></td>
                    <td className="p-4 font-mono text-sm">{formatNumber(game.gamesPlayed)}</td>
                    <td className="p-4 font-mono text-sm">{formatNumber(game.bestScore)}</td>
                    <td className="p-4 font-mono text-sm">{formatNumber(Math.round(game.averageScore))}</td>
                    <td className="p-4 font-mono text-sm">{game.wins} / {game.losses} / {game.draws}</td>
                    <td className="p-4 font-mono text-sm">{game.winRate.toFixed(1)}%</td>
                    <td className="p-4 font-mono text-sm">{durationLabel(game.totalDurationSeconds)}</td>
                    <td className="p-4 text-xs text-[var(--muted)]">{formatDate(game.lastPlayedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>
    </div>
  );
}
