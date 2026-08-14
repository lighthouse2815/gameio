"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { Brain, RotateCw, ShieldCheck } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button, buttonStyles } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useSession } from "@/features/auth/hooks";
import { gameResultsApi } from "@/features/games/game-results-api";
import { randomSeed } from "@/games/core/seeded-random";
import {
  MemoryMatchEngine,
  MEMORY_PAIR_COUNT,
  sameMemoryState,
  type MemoryState,
} from "@/games/memory-match/engine";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";

const BEST_SCORE_KEY = "gameio.memory-match.best";
const SYMBOLS = ["●", "■", "▲", "★", "♥", "◆", "⬢", "✦"];
type RunMode = "offline" | "online";
type RunStatus = "ready" | "playing" | "over";
type VerificationStatus = "idle" | "submitting" | "accepted" | "rejected";
type ActiveRun = { token: string; mode: RunMode; sessionId?: string; actions: string[]; startedAt: number; submitted: boolean };

export default function MemoryMatchGame() {
  const session = useSession();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { t, formatNumber } = useI18n();
  const engineRef = useRef<MemoryMatchEngine | null>(null);
  const activeRun = useRef<ActiveRun | null>(null);
  const startingRef = useRef(false);
  const [gameState, setGameState] = useState<MemoryState>(() => new MemoryMatchEngine(1).state());
  const [runStatus, setRunStatus] = useState<RunStatus>("ready");
  const [mode, setMode] = useState<RunMode>("offline");
  const [starting, setStarting] = useState(false);
  const [verification, setVerification] = useState<VerificationStatus>("idle");
  const [best, setBest] = useState(() => {
    if (typeof window === "undefined") return 0;
    const value = Number(window.localStorage.getItem(BEST_SCORE_KEY));
    return Number.isFinite(value) && value > 0 ? value : 0;
  });

  const beginRun = useCallback((engine: MemoryMatchEngine, runMode: RunMode, sessionId?: string) => {
    engineRef.current = engine;
    activeRun.current = { token: `${runMode}-${Date.now()}`, mode: runMode, sessionId, actions: [], startedAt: Date.now(), submitted: false };
    setGameState(engine.state());
    setMode(runMode);
    setVerification("idle");
    setRunStatus("playing");
  }, []);
  const startOffline = useCallback(() => beginRun(new MemoryMatchEngine(randomSeed()), "offline"), [beginRun]);
  const startOnline = useCallback(async () => {
    if (!session.data || startingRef.current) return;
    startingRef.current = true;
    setStarting(true);
    try {
      const gameSession = await gameResultsApi.createSession("memory-match");
      const engine = new MemoryMatchEngine(gameSession.seed);
      if (!sameMemoryState(engine.state(), gameSession.initialState)) throw new Error("The server returned an inconsistent seeded Memory Match state.");
      beginRun(engine, "online", gameSession.sessionId);
    } catch (error) {
      toast({ title: t("Memory Match online session unavailable"), description: t(getErrorMessage(error)), tone: "error" });
    } finally {
      startingRef.current = false;
      setStarting(false);
    }
  }, [beginRun, session.data, t, toast]);

  const submitOnlineResult = useCallback(async (runToken: string) => {
    const run = activeRun.current;
    if (!run || run.token !== runToken || run.mode !== "online" || !run.sessionId || run.submitted) return;
    run.submitted = true;
    setVerification("submitting");
    try {
      const result = await gameResultsApi.complete({ sessionId: run.sessionId, actions: [...run.actions], durationSeconds: Math.max(1, Math.ceil((Date.now() - run.startedAt) / 1000)) });
      setVerification("accepted");
      toast({ title: t("Memory Match score verified"), description: `${t("Score")} ${formatNumber(result.score)} / +${formatNumber(result.expAwarded)} EXP`, tone: "success" });
      void queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
      void queryClient.invalidateQueries({ queryKey: ["games"] });
      void queryClient.invalidateQueries({ queryKey: ["profile"] });
    } catch (error) {
      setVerification("rejected");
      toast({ title: t("Memory Match verification failed"), description: t(getErrorMessage(error)), tone: "error" });
    }
  }, [formatNumber, queryClient, t, toast]);

  const select = useCallback((index: number) => {
    const engine = engineRef.current;
    const run = activeRun.current;
    if (!engine || !run || runStatus !== "playing" || gameState.pendingMismatch) return;
    const result = engine.select(index);
    if (!result.changed) return;
    if (run.mode === "online") run.actions.push(`S:${index}`);
    setGameState(result.state);
    if (engine.terminal()) {
      setRunStatus("over");
      if (result.state.score > best) {
        setBest(result.state.score);
        window.localStorage.setItem(BEST_SCORE_KEY, String(result.state.score));
      }
      if (run.mode === "online") void submitOnlineResult(run.token);
    }
  }, [best, gameState.pendingMismatch, runStatus, submitOnlineResult]);

  useEffect(() => {
    if (!gameState.pendingMismatch || runStatus !== "playing") return;
    const timeout = window.setTimeout(() => {
      const engine = engineRef.current;
      if (engine) setGameState(engine.clearMismatch());
    }, 700);
    return () => window.clearTimeout(timeout);
  }, [gameState.pendingMismatch, runStatus]);

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,620px)_240px] xl:justify-center">
      <div>
        <div className="mb-3 grid grid-cols-4 gap-px border border-[var(--line)] bg-[var(--line)]">
          {[["Score", formatNumber(gameState.score)], ["Best", formatNumber(best)], ["Moves", formatNumber(gameState.moves)], ["Channel", t(mode)]].map(([label, value]) => <dl className="bg-[var(--surface)] p-3" key={label}><dt className="font-telemetry text-[8px] text-[var(--muted)]">{t(label)}</dt><dd className="mt-1 truncate font-mono text-sm font-bold uppercase">{value}</dd></dl>)}
        </div>
        <div className="relative aspect-square border border-[var(--line-strong)] bg-[var(--line)] p-3 sm:p-5">
          <div className="grid h-full grid-cols-4 gap-2 sm:gap-3" role="grid" aria-label={t("Memory Match board")}>
            {gameState.cells.map((cell, index) => {
              const visible = cell.revealed || cell.matched;
              return <button key={index} type="button" role="gridcell" className={`grid min-h-0 place-items-center border font-mono text-2xl font-black transition-colors sm:text-4xl ${cell.matched ? "border-[var(--accent)] bg-[var(--accent)] text-white" : visible ? "border-[var(--line-strong)] bg-[var(--background)] text-[var(--foreground)]" : "border-[var(--line-strong)] bg-[var(--surface-strong)] text-[var(--muted)] hover:border-[var(--accent)]"}`} aria-label={t("Memory card {card}: {state}", { card: index + 1, state: cell.matched ? t("matched") : visible ? t("revealed") : t("hidden") })} disabled={runStatus !== "playing" || gameState.pendingMismatch || cell.matched || cell.revealed} onClick={() => select(index)}>{visible && cell.value !== null ? SYMBOLS[cell.value] : <span className="font-telemetry text-[10px]">{String(index + 1).padStart(2, "0")}</span>}</button>;
            })}
          </div>
          {runStatus !== "playing" ? <div className="absolute inset-5 grid place-items-center border border-[var(--line-strong)] bg-[var(--surface)]/95 p-6 text-center"><div className="max-w-md"><p className="font-telemetry text-[9px] text-[var(--accent)]">{runStatus === "ready" ? t("[ MEMORY ARRAY / READY ]") : verification === "accepted" ? t("[ SCORE VERIFIED ]") : t("[ MEMORY COMPLETE ]")}</p><h3 className="mt-3 text-4xl font-black uppercase tracking-[-0.055em]">{runStatus === "ready" ? t("Memory Match") : t("All pairs secured")}</h3><p className="mt-3 text-xs leading-5 text-[var(--muted)]">{t("Reveal two cards at a time and remember where every symbol is stored.")}</p><div className="mt-6 flex flex-wrap justify-center gap-2"><Button onClick={startOffline}>{runStatus === "over" ? <RotateCw size={14} aria-hidden="true" /> : <Brain size={14} aria-hidden="true" />}{runStatus === "over" ? t("Retry offline") : t("Play offline")}</Button>{session.data ? <Button variant="secondary" busy={starting || verification === "submitting"} onClick={() => void startOnline()}><ShieldCheck size={14} aria-hidden="true" />{runStatus === "over" ? t("Retry online") : t("Play online")}</Button> : <Link href="/login" className={buttonStyles("secondary")}><ShieldCheck size={14} aria-hidden="true" />{t("Sign in for online rank")}</Link>}</div></div></div> : null}
        </div>
      </div>
      <aside className="border border-[var(--line)] bg-[var(--surface)] p-4"><p className="font-telemetry text-[9px] text-[var(--muted)]">{t("[ MEMORY TELEMETRY ]")}</p><Brain size={28} className="mt-6 text-[var(--accent)]" aria-hidden="true" /><p className="mt-5 text-xs leading-5 text-[var(--muted)]">{t("Matched cards stay exposed. A missed pair remains visible briefly, then returns to the grid.")}</p><dl className="font-telemetry mt-7 grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]"><div className="flex justify-between bg-[var(--background)] p-3"><dt>{t("Pairs")}</dt><dd>{gameState.matchedPairs}/{MEMORY_PAIR_COUNT}</dd></div><div className="flex justify-between bg-[var(--background)] p-3"><dt>{t("Signal")}</dt><dd>{gameState.pendingMismatch ? t("resolving") : t("ready")}</dd></div></dl><p className="font-telemetry mt-6 border-t border-[var(--line)] pt-4 text-[8px] leading-5 text-[var(--muted)]">{mode === "online" ? t("SERVER-SEED / REPLAY VERIFIED") : t("LOCAL PRACTICE / NO RANK")}</p></aside>
    </div>
  );
}
