"use client";

import Link from "next/link";
import { Calendar, Gamepad2, Trophy, Zap } from "lucide-react";
import { buttonStyles } from "@/components/ui/button";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/features/auth/hooks";
import { useRecentGames } from "@/features/games/hooks";
import { profileApi } from "@/features/profile/api";
import { calculateLevelProgress } from "@/features/profile/level-progress";
import { getErrorMessage, isApiError } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";

export function ProfileScreen({ username }: { username: string }) {
  const { t, formatDate, formatDateTime, formatNumber } = useI18n();
  const session = useSession();
  const isOwnProfile = session.data?.username === username;
  const recent = useRecentGames(Boolean(isOwnProfile), 10);
  const profile = useQuery({
    queryKey: ["profile", username],
    queryFn: () => profileApi.byUsername(username),
    enabled: Boolean(username),
  });

  if (profile.isLoading) {
    return (
      <div className="border-x border-b border-[var(--line)] p-5 sm:p-8">
        <Skeleton className="h-[420px]" />
      </div>
    );
  }
  if (profile.isError) {
    return (
      <div className="border-x border-b border-[var(--line)] p-5 sm:p-8">
        <ErrorState
          title={
            isApiError(profile.error) && profile.error.status === 404
              ? "Player not found"
              : "Profile channel unavailable"
          }
          description={t(getErrorMessage(profile.error))}
          onAction={() => void profile.refetch()}
        />
      </div>
    );
  }
  if (!profile.data) return null;
  const player = profile.data;
  const levelProgress = calculateLevelProgress(player.exp, player.level);
  const progress = levelProgress.progressPercent;
  const history = isOwnProfile
    ? (recent.data?.content.map((match) => ({
        id: match.id,
        gameId: match.gameId,
        gameName: match.gameName,
        gameSlug: match.gameSlug,
        score: match.score,
        result: match.result,
        duration: match.durationSeconds,
        playedAt: match.playedAt,
      })) ?? [])
    : [];
  const profileStats = [
    { label: "Level", value: player.level, icon: Zap },
    { label: "Games played", value: player.gamesPlayed, icon: Gamepad2 },
    { label: "Verified wins", value: player.wins, icon: Trophy },
  ];

  return (
    <>
      <section className="grid border-x border-b border-[var(--line)] bg-[var(--surface)] lg:grid-cols-[280px_1fr]">
        <div className="grid min-h-72 place-items-center border-b border-[var(--line)] bg-[var(--surface-strong)] p-8 lg:border-b-0 lg:border-r">
          {player.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={player.avatarUrl}
              alt={t("{username} avatar", { username: player.username })}
              className="aspect-square w-full max-w-48 border border-[var(--line-strong)] object-cover grayscale"
            />
          ) : (
            <div className="grid aspect-square w-full max-w-48 place-items-center border border-[var(--line-strong)] bg-[var(--background)] text-7xl font-black uppercase tracking-[-0.08em]">
              {player.username.slice(0, 2)}
            </div>
          )}
        </div>
        <div className="flex flex-col justify-between p-5 sm:p-9 lg:p-12">
          <div>
            <p className="font-telemetry text-[9px] text-[var(--accent)]">
              PLAYER / {player.id}
            </p>
            <h1 className="page-title mt-6">{player.username}</h1>
          </div>
          <div className="mt-14">
            <div className="font-telemetry mb-2 flex justify-between text-[8px] text-[var(--muted)]">
              <span>{t("LEVEL")} {player.level} / EXP {formatNumber(player.exp)}</span>
              <span>{progress}%</span>
            </div>
            <div className="h-3 border border-[var(--line-strong)] bg-[var(--background)] p-[2px]">
              <div className="h-full bg-[var(--accent)]" style={{ width: progress + "%" }} />
            </div>
          </div>
        </div>
      </section>

      <dl className="grid gap-px border-x border-b border-[var(--line)] bg-[var(--line)] sm:grid-cols-3">
        {profileStats.map(({ label, value, icon: Icon }) => (
          <div className="bg-[var(--background)] p-5 sm:p-7" key={label}>
            <Icon size={18} className="text-[var(--accent)]" aria-hidden="true" />
            <dt className="font-telemetry mt-8 text-[8px] text-[var(--muted)]">{t(label)}</dt>
            <dd className="mt-1 text-4xl font-black tracking-[-0.05em]">{formatNumber(value)}</dd>
          </div>
        ))}
      </dl>

      <section className="mt-20 grid border-x border-t border-[var(--line)] lg:grid-cols-[1fr_360px]">
        <div className="border-b border-[var(--line)] bg-[var(--surface)] lg:border-b-0 lg:border-r">
          <header className="border-b border-[var(--line)] p-5 sm:p-7">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("[ MATCH LOG ]")}</p>
            <h2 className="mt-2 text-3xl font-black uppercase tracking-[-0.05em]">{t("Recent history")}</h2>
          </header>
          {isOwnProfile && recent.isLoading ? (
            <Skeleton className="m-5 h-64" />
          ) : null}
          {isOwnProfile && recent.isError ? (
            <div className="p-5">
              <ErrorState
                title={t("Match history unavailable")}
                description={t(getErrorMessage(recent.error))}
                onAction={() => void recent.refetch()}
              />
            </div>
          ) : null}
          {!recent.isLoading && !recent.isError && history.length ? (
            <ul>
              {history.map((match) => (
                <li className="grid gap-4 border-b border-[var(--line)] p-5 last:border-b-0 sm:grid-cols-[1fr_auto_auto] sm:items-center" key={match.id}>
                  <div>
                    <Link href={"/game/" + match.gameSlug} className="font-bold uppercase hover:text-[var(--accent)]">
                      {t(match.gameName)}
                    </Link>
                    <p className="font-telemetry mt-1 text-[8px] text-[var(--muted)]">
                      {formatDateTime(match.playedAt)}
                    </p>
                  </div>
                  <span className="font-telemetry text-[9px]">{formatNumber(match.score)} {t("PTS")}</span>
                  <span className="font-telemetry border border-[var(--line-strong)] px-2 py-1 text-[8px]">{t(match.result)}</span>
                </li>
              ))}
            </ul>
          ) : !recent.isLoading && !recent.isError ? (
            <div className="p-5">
              <EmptyState
                title={isOwnProfile ? "No completed runs" : "History is private"}
                description={isOwnProfile ? "Completed server-recorded operations will appear here." : "Only the profile owner can inspect detailed match history."}
              />
            </div>
          ) : null}
        </div>
        <aside className="bg-[var(--background)] p-6">
          <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("[ ACHIEVEMENT LOG ]")}</p>
          <h2 className="mt-2 text-2xl font-black uppercase tracking-[-0.04em]">{t("Unlocked")}</h2>
          {player.achievements.length ? (
            <ul className="mt-6 grid gap-px border border-[var(--line)] bg-[var(--line)]">
              {player.achievements.map((achievement) => (
                <li className="bg-[var(--surface)] p-4" key={achievement.id}>
                  <p className="font-telemetry text-[8px] text-[var(--accent)]">{achievement.code}</p>
                  <p className="mt-2 text-sm font-bold uppercase">{t(achievement.name)}</p>
                  <p className="mt-2 text-xs leading-5 text-[var(--muted)]">{t(achievement.description)}</p>
                </li>
              ))}
            </ul>
          ) : (
            <div className="mt-6 border border-dashed border-[var(--line-strong)] p-5 text-xs leading-5 text-[var(--muted)]">
              {t("No achievements unlocked.")}
            </div>
          )}
          {player.createdAt ? (
            <p className="font-telemetry mt-8 flex items-center gap-2 text-[8px] text-[var(--muted)]">
              <Calendar size={12} aria-hidden="true" />
              {t("JOINED")} {formatDate(player.createdAt)}
            </p>
          ) : null}
          <Link href="/friends" className={buttonStyles("secondary") + " mt-6 w-full"}>
            {t("Player network")}
          </Link>
        </aside>
      </section>
    </>
  );
}
