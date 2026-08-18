"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { RotateCw, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { playFeedback } from "@/features/settings/player-feedback";
import { useI18n } from "@/lib/i18n/use-i18n";
import { TYPING_LESSONS } from "@/games/typing-race/corpus";
import {
  applyPracticeCharacter,
  beginTypingPractice,
  createTypingPractice,
  toGraphemes,
  typingMetrics,
} from "@/games/typing-race/engine";
import { TypingStage } from "@/games/typing-race/typing-stage";

const COUNTDOWN_MS = 3_000;

export default function TypingRacePractice() {
  const { t } = useI18n();
  const [lessonId, setLessonId] = useState(TYPING_LESSONS[0].id);
  const lesson = TYPING_LESSONS.find((entry) => entry.id === lessonId) ?? TYPING_LESSONS[0];
  const [run, setRun] = useState(() => createTypingPractice(lesson.prompt));
  const [nowMs, setNowMs] = useState(0);
  const [countdownEndsAt, setCountdownEndsAt] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<"idle" | "correct" | "error" | "finish">("idle");
  const [feedbackId, setFeedbackId] = useState(0);
  const [focused, setFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (run.phase !== "countdown" && run.phase !== "playing") return;
    const update = () => {
      const now = performance.now();
      setNowMs(now);
      if (run.phase === "countdown" && countdownEndsAt !== null && now >= countdownEndsAt) {
        setRun((current) => beginTypingPractice(current, countdownEndsAt));
        requestAnimationFrame(() => inputRef.current?.focus());
      }
    };
    update();
    const timer = window.setInterval(update, 50);
    return () => window.clearInterval(timer);
  }, [countdownEndsAt, run.phase]);

  const metrics = typingMetrics(run, nowMs);
  const countdown = countdownEndsAt === null
    ? 3
    : Math.max(1, Math.ceil((countdownEndsAt - nowMs) / 1_000));

  const start = useCallback(() => {
    const next = createTypingPractice(lesson.prompt);
    const now = performance.now();
    setRun({ ...next, phase: "countdown" });
    setNowMs(now);
    setCountdownEndsAt(now + COUNTDOWN_MS);
    setFeedback("idle");
    setFocused(false);
  }, [lesson.prompt]);

  const handleCharacter = useCallback((character: string) => {
    const now = performance.now();
    setRun((current) => {
      const result = applyPracticeCharacter(current, character, now);
      if (!result.changed) return current;
      const complete = result.state.phase === "complete";
      setFeedback(complete ? "finish" : result.correct ? "correct" : "error");
      setFeedbackId((value) => value + 1);
      playFeedback(complete ? "success" : result.correct ? "typing" : "error");
      return result.state;
    });
  }, []);

  function inputCharacter(event: FormEvent<HTMLInputElement>) {
    if (run.phase !== "playing") return;
    const native = event.nativeEvent as InputEvent;
    if (native.inputType !== "insertText" || native.data === null) return;
    const characters = toGraphemes(native.data);
    if (characters.length === 1) handleCharacter(characters[0]);
  }

  const statusText = run.phase === "complete"
    ? t("Practice complete")
    : run.phase === "playing"
      ? focused ? t("Flow active") : t("Click the input field to keep typing")
      : run.phase === "countdown" ? t("Get ready") : t("Choose a lesson and start");

  const input = (
    <input
      ref={inputRef}
      value=""
      onChange={() => undefined}
      onInput={inputCharacter}
      onPaste={(event) => {
        if (run.phase === "playing") {
          event.preventDefault();
          setFeedback("error");
          setFeedbackId((value) => value + 1);
          playFeedback("error");
        }
      }}
      onDrop={(event) => event.preventDefault()}
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
      disabled={run.phase !== "playing"}
      autoCapitalize="off"
      autoComplete="off"
      autoCorrect="off"
      spellCheck={false}
      aria-label={t("Typing input")}
      placeholder={run.phase === "playing" ? t("Click here and type") : t("Start the run first")}
      className="font-telemetry min-h-11 w-full border border-[var(--line-strong)] bg-[var(--surface)] px-3 text-[10px] text-[var(--foreground)] placeholder:text-[var(--muted)] disabled:cursor-not-allowed disabled:opacity-55"
    />
  );

  const racers = useMemo(() => [{
    id: "practice",
    name: t("You"),
    own: true,
    progressPercent: metrics.progressPercent,
    wpm: metrics.wpm,
    accuracyPercent: metrics.accuracyPercent,
    finished: run.phase === "complete",
  }], [metrics.accuracyPercent, metrics.progressPercent, metrics.wpm, run.phase, t]);

  return (
    <div className="grid gap-5">
      <section className="grid gap-px border border-[var(--line)] bg-[var(--line)] lg:grid-cols-[1fr_auto]">
        <div className="bg-[var(--surface)] p-5">
          <p className="font-telemetry text-[8px] text-[var(--accent)]">{t("[ TEN-FINGER TRAINING ]")}</p>
          <h3 className="mt-2 text-3xl font-black uppercase tracking-[-0.05em]">{t(lesson.title)}</h3>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-[var(--muted)]">{t(lesson.description)}</p>
          <div className="mt-5 flex flex-wrap gap-2">
            {TYPING_LESSONS.map((entry) => (
              <Button
                compact
                variant={entry.id === lessonId ? "primary" : "secondary"}
                key={entry.id}
                disabled={run.phase === "countdown" || run.phase === "playing"}
                onClick={() => {
                  setLessonId(entry.id);
                  setRun(createTypingPractice(entry.prompt));
                }}
              >
                {t(entry.title)}
              </Button>
            ))}
          </div>
        </div>
        <div className="flex min-w-60 items-center bg-[var(--background)] p-5">
          <Button className="w-full" onClick={start} disabled={run.phase === "countdown" || run.phase === "playing"}>
            {run.phase === "complete" ? <RotateCw size={14} aria-hidden="true" /> : <Sparkles size={14} aria-hidden="true" />}
            {t(run.phase === "complete" ? "Run it again" : "Start practice")}
          </Button>
        </div>
      </section>

      <TypingStage
        prompt={run.prompt}
        cursor={run.cursor}
        wpm={metrics.wpm}
        accuracyPercent={metrics.accuracyPercent}
        mistakes={run.mistakes}
        combo={run.combo}
        bestCombo={run.bestCombo}
        phase={run.phase}
        countdown={countdown}
        racers={racers}
        feedback={feedback}
        feedbackId={feedbackId}
        inputSlot={input}
        statusText={statusText}
      />

      <p className="font-telemetry text-[8px] leading-5 text-[var(--muted)]">
        {t("Local practice stays on this device. It does not create rank, EXP, or a verified result.")}
      </p>
    </div>
  );
}
