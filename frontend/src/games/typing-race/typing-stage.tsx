"use client";

import type { ReactNode } from "react";
import { Flame, Gauge, Keyboard, Target, Zap } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/cn";
import { useI18n } from "@/lib/i18n/use-i18n";
import { toGraphemes } from "@/games/typing-race/engine";
import styles from "@/games/typing-race/typing-race.module.css";

export type TypingRacer = {
  id: string;
  name: string;
  progressPercent: number;
  wpm: number;
  accuracyPercent: number;
  own?: boolean;
  finished?: boolean;
};

const KEY_ROWS = [
  ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
  ["a", "s", "d", "f", "g", "h", "j", "k", "l", ";"],
  ["z", "x", "c", "v", "b", "n", "m", ",", ".", "/"],
] as const;

const FINGERS: Readonly<Record<string, string>> = {
  q: "Left little finger", a: "Left little finger", z: "Left little finger",
  w: "Left ring finger", s: "Left ring finger", x: "Left ring finger",
  e: "Left middle finger", d: "Left middle finger", c: "Left middle finger",
  r: "Left index finger", f: "Left index finger", v: "Left index finger",
  t: "Left index finger", g: "Left index finger", b: "Left index finger",
  y: "Right index finger", h: "Right index finger", n: "Right index finger",
  u: "Right index finger", j: "Right index finger", m: "Right index finger",
  i: "Right middle finger", k: "Right middle finger", ",": "Right middle finger",
  o: "Right ring finger", l: "Right ring finger", ".": "Right ring finger",
  p: "Right little finger", ";": "Right little finger", "/": "Right little finger",
  " ": "Thumb",
};

export function TypingStage({
  prompt,
  cursor,
  wpm,
  accuracyPercent,
  mistakes,
  combo,
  bestCombo,
  phase,
  countdown,
  racers,
  feedback,
  feedbackId,
  inputSlot,
  statusText,
}: {
  prompt: string;
  cursor: number;
  wpm: number;
  accuracyPercent: number;
  mistakes: number;
  combo: number;
  bestCombo: number;
  phase: "ready" | "countdown" | "playing" | "complete";
  countdown?: number;
  racers: TypingRacer[];
  feedback: "idle" | "correct" | "error" | "finish";
  feedbackId: number;
  inputSlot: ReactNode;
  statusText: string;
}) {
  const { t, formatNumber } = useI18n();
  const characters = toGraphemes(prompt);
  const nextCharacter = characters[cursor] ?? "";
  const keyTarget = nextCharacter.toLowerCase();
  const progressPercent = characters.length
    ? Math.round((cursor / characters.length) * 100)
    : 0;
  const statCards: Array<{ label: string; value: string | number; Icon: LucideIcon }> = [
    { label: "WPM", value: wpm, Icon: Gauge },
    { label: "Accuracy", value: `${accuracyPercent}%`, Icon: Target },
    { label: "Mistakes", value: mistakes, Icon: Zap },
    { label: "Best combo", value: bestCombo, Icon: Flame },
  ];

  return (
    <div className={styles.stage}>
      <span
        key={feedbackId}
        className={cn(
          styles.feedbackLayer,
          feedback === "correct" && styles.correctPulse,
          feedback === "error" && styles.errorPulse,
          feedback === "finish" && styles.finishPulse,
        )}
        aria-hidden="true"
      />
      <div className="grid gap-px bg-[var(--line)] lg:grid-cols-4">
        {statCards.map(({ label, value, Icon }) => (
          <div className="bg-[var(--surface)] p-4" key={label}>
            <div className="font-telemetry flex items-center justify-between text-[8px] text-[var(--muted)]">
              <span>{t(label)}</span>
              <Icon size={14} className="text-[var(--accent)]" aria-hidden="true" />
            </div>
            <p className="mt-2 text-3xl font-black tracking-[-0.05em]">
              {typeof value === "number" ? formatNumber(value) : value}
            </p>
          </div>
        ))}
      </div>

      <section className="relative border-t border-[var(--line)] p-5 sm:p-8 lg:p-10">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <p className="font-telemetry text-[8px] text-[var(--accent)]">
            {t("[ RHYTHM STREAM ]")}
          </p>
          <p className="font-telemetry text-[8px] text-[var(--muted)]" aria-live="polite">
            {statusText} / {progressPercent}% / {t("Combo")} ×{combo}
          </p>
        </div>
        <p className={styles.prompt} aria-label={t("Typing passage")}>
          {characters.map((character, index) => (
            <span
              className={
                index < cursor
                  ? styles.typed
                  : index === cursor
                    ? styles.current
                    : styles.remaining
              }
              key={`${index}-${character}`}
            >
              {character}
            </span>
          ))}
        </p>
        {phase === "countdown" ? (
          <div className="absolute inset-0 grid place-items-center bg-[var(--background)]/90" role="status">
            <div className="text-center">
              <p className="font-telemetry text-[9px] text-[var(--muted)]">{t("Fingers on home row")}</p>
              <p className="mt-3 text-8xl font-black text-[var(--accent)]">{countdown ?? 3}</p>
            </div>
          </div>
        ) : null}
      </section>

      <div className="border-t border-[var(--line)] bg-[var(--surface)] p-4 sm:p-6">
        <div className="h-2 border border-[var(--line-strong)] bg-[var(--background)] p-px" role="progressbar" aria-label={t("Typing progress")} aria-valuemin={0} aria-valuemax={100} aria-valuenow={progressPercent}>
          <div className={styles.energy} style={{ transform: `scaleX(${progressPercent / 100})` }} />
        </div>

        <div className="mt-5 grid gap-3">
          {racers.map((racer) => (
            <div className="grid grid-cols-[100px_1fr_auto] items-center gap-3" key={racer.id}>
              <p className="truncate text-xs font-bold uppercase">{racer.own ? t("You") : racer.name}</p>
              <div className="relative h-5 border-y border-[var(--line)]">
                <span
                  className={cn(styles.laneMarker, racer.own ? "text-[var(--accent)]" : "text-[var(--online)]")}
                  style={{ right: `${100 - Math.max(0, Math.min(100, racer.progressPercent))}%` }}
                  aria-hidden="true"
                />
              </div>
              <p className="font-telemetry min-w-20 text-right text-[8px] text-[var(--muted)]">
                {racer.wpm} WPM / {racer.accuracyPercent}%
              </p>
            </div>
          ))}
        </div>
      </div>

      <div className="grid gap-px border-t border-[var(--line)] bg-[var(--line)] lg:grid-cols-[1fr_260px]">
        <div className="hidden bg-[var(--surface)] p-5 sm:block">
          <div className="mx-auto grid max-w-2xl gap-2">
            {KEY_ROWS.map((row, rowIndex) => (
              <div className="grid grid-cols-10 gap-2" style={{ paddingInline: `${rowIndex * 2.5}%` }} key={row.join("")}>
                {row.map((key) => (
                  <span className={cn(styles.key, key === keyTarget && styles.targetKey)} key={key}>{key}</span>
                ))}
              </div>
            ))}
            <div className="grid place-items-center pt-1">
              <span className={cn(styles.key, "w-1/2", keyTarget === " " && styles.targetKey)}>{t("Space")}</span>
            </div>
          </div>
        </div>
        <aside className="bg-[var(--background)] p-5">
          <Keyboard size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <p className="font-telemetry mt-5 text-[8px] text-[var(--muted)]">{t("[ NEXT INPUT ]")}</p>
          <p className="mt-2 text-5xl font-black uppercase">{nextCharacter === " " ? t("Space") : nextCharacter || "—"}</p>
          <p className="mt-3 text-xs leading-5 text-[var(--muted)]">
            {t(FINGERS[keyTarget] ?? "Keep a relaxed hand position")}
          </p>
          <div className="mt-5">{inputSlot}</div>
        </aside>
      </div>
    </div>
  );
}
