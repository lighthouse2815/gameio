"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Award, RotateCcw, Share2, Sparkles, Trophy, X } from "lucide-react";
import { Button, buttonStyles } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import type { GameResultSummary } from "@/features/games/types";
import { useI18n } from "@/lib/i18n/use-i18n";

export function SoloResultSummary({ gameSlug }: { gameSlug: string }) {
  const [result, setResult] = useState<GameResultSummary | null>(null);
  const toast = useToast();
  const { t, formatNumber } = useI18n();

  useEffect(() => {
    const receive = (event: Event) => {
      const next = (event as CustomEvent<GameResultSummary>).detail;
      if (next?.gameSlug === gameSlug) setResult(next);
    };
    window.addEventListener("gameio:verified-result", receive);
    return () => window.removeEventListener("gameio:verified-result", receive);
  }, [gameSlug]);

  if (!result) return null;

  const improvement =
    result.previousBestScore == null
      ? null
      : result.score - result.previousBestScore;
  const shareText = t(
    "I scored {score} in {game} on Gameio. Can you beat it?",
    { score: formatNumber(result.score), game: t(result.gameName) },
  );

  async function share() {
    try {
      if (navigator.share) {
        await navigator.share({
          title: t("Gameio verified result"),
          text: shareText,
          url: window.location.href,
        });
      } else {
        await navigator.clipboard.writeText(`${shareText} ${window.location.href}`);
        toast({
          title: t("Result copied"),
          description: t("The verified score and game link are ready to paste."),
          tone: "success",
        });
      }
    } catch (error) {
      if ((error as DOMException).name !== "AbortError") {
        toast({
          title: t("Could not share result"),
          description: t("Your browser blocked the share action. Try again from a secure tab."),
          tone: "error",
        });
      }
    }
  }

  return (
    <section
      className="mt-4 border border-[var(--accent)] bg-[var(--background)]"
      aria-live="polite"
      aria-label={t("Verified run summary")}
    >
      <header className="flex items-start justify-between gap-4 border-b border-[var(--line)] p-4 sm:p-5">
        <div>
          <p className="font-telemetry text-[8px] text-[var(--accent)]">
            {t(result.offline ? "[ OFFLINE RUN COMPLETE ]" : "[ VERIFIED RUN COMPLETE ]")}
          </p>
          <h3 className="mt-2 text-2xl font-black uppercase tracking-[-0.045em] sm:text-3xl">
            {result.personalBest ? t("New personal best") : t("Run recorded")}
          </h3>
        </div>
        <button
          type="button"
          onClick={() => setResult(null)}
          className="grid h-10 w-10 place-items-center border border-[var(--line)] text-[var(--muted)] hover:border-[var(--accent)] hover:text-[var(--foreground)]"
          aria-label={t("Close result summary")}
        >
          <X size={16} aria-hidden="true" />
        </button>
      </header>

      <dl className="grid gap-px bg-[var(--line)] sm:grid-cols-4">
        <div className="bg-[var(--surface)] p-4">
          <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Score")}</dt>
          <dd className="mt-2 text-3xl font-black">{formatNumber(result.score)}</dd>
        </div>
        <div className="bg-[var(--surface)] p-4">
          <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Best delta")}</dt>
          <dd className="mt-2 text-3xl font-black text-[var(--accent)]">
            {improvement == null ? t("FIRST") : improvement > 0 ? `+${formatNumber(improvement)}` : formatNumber(improvement)}
          </dd>
        </div>
        <div className="bg-[var(--surface)] p-4">
          <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Reward")}</dt>
          <dd className="mt-2 text-3xl font-black">+{formatNumber(result.expAwarded)} EXP</dd>
        </div>
        <div className="bg-[var(--surface)] p-4">
          <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Level")}</dt>
          <dd className="mt-2 text-3xl font-black">{formatNumber(result.resultingLevel)}</dd>
        </div>
      </dl>

      {result.unlockedAchievements.length ? (
        <div className="border-t border-[var(--line)] p-4 sm:p-5">
          <p className="font-telemetry flex items-center gap-2 text-[8px] text-[var(--accent)]">
            <Sparkles size={13} aria-hidden="true" />
            {t("Achievements unlocked in this run")}
          </p>
          <ul className="mt-3 grid gap-2 sm:grid-cols-2">
            {result.unlockedAchievements.map((achievement) => (
              <li key={achievement.id} className="flex items-center gap-3 border border-[var(--line)] p-3">
                <Award size={18} className="shrink-0 text-[var(--accent)]" aria-hidden="true" />
                <span>
                  <strong className="block text-sm uppercase">{t(achievement.name)}</strong>
                  <span className="font-telemetry text-[8px] text-[var(--muted)]">+{formatNumber(achievement.expReward)} EXP</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <footer className="flex flex-wrap gap-2 border-t border-[var(--line)] p-4 sm:p-5">
        {result.offline ? (
          <p className="w-full pb-2 text-xs leading-5 text-[var(--muted)]">
            {t("Offline scores stay on this device and never enter verified rankings or account progression.")}
          </p>
        ) : null}
        <Button onClick={() => window.location.reload()}>
          <RotateCcw size={14} aria-hidden="true" />
          {t("Play again")}
        </Button>
        <Button variant="secondary" onClick={() => void share()}>
          <Share2 size={14} aria-hidden="true" />
          {t("Share result")}
        </Button>
        {!result.offline ? <Link href={`/leaderboard?game=${encodeURIComponent(result.gameId)}`} className={buttonStyles("ghost")}>
          <Trophy size={14} aria-hidden="true" />
          {t("View ranks")}
        </Link> : null}
      </footer>
    </section>
  );
}
