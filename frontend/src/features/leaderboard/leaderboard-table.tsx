"use client";

import Link from "next/link";
import type { LeaderboardEntry } from "@/features/leaderboard/types";
import { useI18n } from "@/lib/i18n/use-i18n";

export function LeaderboardTable({
  entries,
  compact = false,
}: {
  entries: LeaderboardEntry[];
  compact?: boolean;
}) {
  const { t, formatNumber } = useI18n();
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] border-collapse text-left">
        <thead className="font-telemetry text-[8px] text-[var(--muted)]">
          <tr className="border-b border-[var(--line)]">
            <th className="w-20 px-4 py-3 font-medium">{t("Rank")}</th>
            <th className="px-4 py-3 font-medium">{t("Player")}</th>
            <th className="px-4 py-3 text-right font-medium">{t("Score")}</th>
            <th className="px-4 py-3 text-right font-medium">{t("Wins")}</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <tr
              key={entry.userId ?? entry.username}
              className="group border-b border-[var(--line)] last:border-b-0 hover:bg-[var(--surface-strong)]"
            >
              <td className="px-4 py-4">
                <span
                  className={
                    "font-telemetry text-xs font-bold " +
                    (entry.rank <= 3
                      ? "text-[var(--accent)]"
                      : "text-[var(--muted)]")
                  }
                >
                  {String(entry.rank).padStart(2, "0")}
                </span>
              </td>
              <td className="px-4 py-4">
                <Link
                  href={"/profile/" + encodeURIComponent(entry.username)}
                  className="flex items-center gap-3"
                >
                  {entry.avatarUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={entry.avatarUrl}
                      alt=""
                      className="h-8 w-8 border border-[var(--line-strong)] object-cover grayscale"
                    />
                  ) : (
                    <span className="font-telemetry grid h-8 w-8 place-items-center border border-[var(--line-strong)] bg-[var(--background)] text-[9px]">
                      {entry.username.slice(0, 2).toUpperCase()}
                    </span>
                  )}
                  <span className="font-semibold group-hover:text-[var(--accent)]">
                    {entry.username}
                  </span>
                  {entry.level !== undefined ? (
                    <span className="font-telemetry text-[8px] text-[var(--muted)]">
                      L{entry.level}
                    </span>
                  ) : null}
                </Link>
              </td>
              <td className="font-telemetry px-4 py-4 text-right text-[10px]">
                {formatNumber(entry.score)}
              </td>
              <td className="font-telemetry px-4 py-4 text-right text-[10px] text-[var(--muted)]">
                {formatNumber(entry.wins)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!compact ? (
        <div className="font-telemetry border-t border-[var(--line)] px-4 py-3 text-[8px] text-[var(--muted)]">
          {t("RANKS ARE SERVER-VERIFIED / CLIENT CLAIMS REJECTED")}
        </div>
      ) : null}
    </div>
  );
}
