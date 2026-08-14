"use client";

import { useEffect, useState, type ComponentType } from "react";
import { useRouter } from "next/navigation";
import { CloudOff, Download, ShieldOff, Wifi, WifiOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SoloResultSummary } from "@/features/games/solo-result-summary";
import { usePwaStore } from "@/features/pwa/pwa-store";
import Game2048 from "@/games/game2048/game2048";
import SnakeGame from "@/games/snake/snake-game";
import FlappyBirdGame from "@/games/flappy-bird/flappy-bird-game";
import BreakoutGame from "@/games/breakout/breakout-game";
import MinesweeperGame from "@/games/minesweeper/minesweeper-game";
import MemoryMatchGame from "@/games/memory-match/memory-match-game";
import { useI18n } from "@/lib/i18n/use-i18n";

const OFFLINE_GAMES: Array<{ slug: string; name: string; engine: ComponentType }> = [
  { slug: "2048", name: "2048", engine: Game2048 },
  { slug: "snake", name: "Snake", engine: SnakeGame },
  { slug: "flappy-bird", name: "Flappy Bird", engine: FlappyBirdGame },
  { slug: "breakout", name: "Breakout", engine: BreakoutGame },
  { slug: "minesweeper", name: "Minesweeper", engine: MinesweeperGame },
  { slug: "memory-match", name: "Memory Match", engine: MemoryMatchGame },
];

export function OfflineHub({ initialSlug }: { initialSlug?: string }) {
  const router = useRouter();
  const { t } = useI18n();
  const [online, setOnline] = useState(true);
  const selected = OFFLINE_GAMES.find((game) => game.slug === initialSlug) ?? OFFLINE_GAMES[0];
  const installPrompt = usePwaStore((state) => state.installPrompt);
  const install = usePwaStore((state) => state.install);
  const Engine = selected.engine;

  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    update();
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);

  return (
    <div className="border-x border-b border-[var(--line)] bg-[var(--surface)]">
      <section className="grid gap-px border-b border-[var(--line)] bg-[var(--line)] md:grid-cols-[1fr_auto]">
        <div className="bg-[var(--background)] p-5 sm:p-6">
          <p className="font-telemetry flex items-center gap-2 text-[9px] text-[var(--accent)]">
            <CloudOff size={14} aria-hidden="true" />
            {t("LOCAL-ONLY PLAY MODE")}
          </p>
          <p className="mt-3 max-w-3xl text-sm leading-6 text-[var(--muted)]">
            {t("These six engines run entirely on this device. Offline scores are kept locally and are never uploaded into verified rankings, achievements, EXP, or Daily Challenge progress.")}
          </p>
        </div>
        <div className="flex min-w-64 items-center justify-between gap-5 bg-[var(--surface)] p-5">
          <span className="font-telemetry flex items-center gap-2 text-[9px]">
            {online ? <Wifi size={14} className="text-[var(--online)]" aria-hidden="true" /> : <WifiOff size={14} className="text-[var(--accent)]" aria-hidden="true" />}
            {t(online ? "Network available" : "Device offline")}
          </span>
          {installPrompt ? (
            <Button compact onClick={() => void install()}>
              <Download size={13} aria-hidden="true" />
              {t("Install")}
            </Button>
          ) : null}
        </div>
      </section>

      <nav className="grid gap-px border-b border-[var(--line)] bg-[var(--line)] sm:grid-cols-3 lg:grid-cols-6" aria-label={t("Offline game selection")}>
        {OFFLINE_GAMES.map((game, index) => (
          <button
            key={game.slug}
            type="button"
            onClick={() => router.replace(`/offline?game=${encodeURIComponent(game.slug)}`)}
            className={
              "min-h-20 bg-[var(--surface)] p-3 text-left transition-colors hover:bg-[var(--surface-strong)] " +
              (game.slug === selected.slug ? "shadow-[inset_0_-4px_0_var(--accent)]" : "")
            }
            aria-pressed={game.slug === selected.slug}
          >
            <span className="font-telemetry block text-[8px] text-[var(--muted)]">0{index + 1} / LOCAL</span>
            <span className="mt-2 block text-sm font-black uppercase">{t(game.name)}</span>
          </button>
        ))}
      </nav>

      <section className="p-3 sm:p-6 lg:p-8" key={selected.slug}>
        <header className="mb-4 flex flex-wrap items-center justify-between gap-3 border border-[var(--line)] bg-[var(--background)] p-4">
          <div>
            <p className="font-telemetry text-[8px] text-[var(--accent)]">{t("[ OFFLINE ENGINE ]")}</p>
            <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t(selected.name)}</h2>
          </div>
          <p className="font-telemetry flex items-center gap-2 text-[8px] text-[var(--muted)]">
            <ShieldOff size={14} aria-hidden="true" />
            {t("NO RANKING SYNC")}
          </p>
        </header>
        <Engine />
        <SoloResultSummary gameSlug={selected.slug} />
      </section>
    </div>
  );
}
