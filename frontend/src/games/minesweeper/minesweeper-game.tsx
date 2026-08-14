"use client";

import Link from "next/link";
import { useCallback, useRef, useState } from "react";
import { Bomb, Flag, RotateCw, ScanSearch, ShieldCheck } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button, buttonStyles } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useSession } from "@/features/auth/hooks";
import { gameResultsApi } from "@/features/games/game-results-api";
import { randomSeed } from "@/games/core/seeded-random";
import {
  MinesweeperEngine,
  MINESWEEPER_COLUMNS,
  sameMinesweeperState,
  type MinesweeperState,
} from "@/games/minesweeper/engine";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";

const BEST_SCORE_KEY = "gameio.minesweeper.best";
type RunMode = "offline" | "online";
type RunStatus = "ready" | "playing" | "over";
type VerificationStatus = "idle" | "submitting" | "accepted" | "rejected";
type ActiveRun = { token: string; mode: RunMode; sessionId?: string; actions: string[]; startedAt: number; submitted: boolean };

export default function MinesweeperGame() {
  const session = useSession();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { t, formatNumber } = useI18n();
  const engineRef = useRef<MinesweeperEngine | null>(null);
  const activeRun = useRef<ActiveRun | null>(null);
  const startingRef = useRef(false);
  const [gameState, setGameState] = useState<MinesweeperState>(() => new MinesweeperEngine(1).state());
  const [runStatus, setRunStatus] = useState<RunStatus>("ready");
  const [mode, setMode] = useState<RunMode>("offline");
  const [flagMode, setFlagMode] = useState(false);
  const [starting, setStarting] = useState(false);
  const [verification, setVerification] = useState<VerificationStatus>("idle");
  const [best, setBest] = useState(() => {
    if (typeof window === "undefined") return 0;
    const value = Number(window.localStorage.getItem(BEST_SCORE_KEY));
    return Number.isFinite(value) && value > 0 ? value : 0;
  });

  const beginRun = useCallback((engine: MinesweeperEngine, runMode: RunMode, sessionId?: string) => {
    const initialState = engine.state();
    engineRef.current = engine;
    activeRun.current = { token: `${runMode}-${Date.now()}`, mode: runMode, sessionId, actions: [], startedAt: Date.now(), submitted: false };
    setGameState(initialState);
    setMode(runMode);
    setFlagMode(false);
    setVerification("idle");
    setRunStatus("playing");
  }, []);

  const startOffline = useCallback(() => beginRun(new MinesweeperEngine(randomSeed()), "offline"), [beginRun]);
  const startOnline = useCallback(async () => {
    if (!session.data || startingRef.current) return;
    startingRef.current = true;
    setStarting(true);
    try {
      const gameSession = await gameResultsApi.createSession("minesweeper");
      const engine = new MinesweeperEngine(gameSession.seed);
      if (!sameMinesweeperState(engine.state(), gameSession.initialState)) throw new Error("The server returned an inconsistent seeded Minesweeper state.");
      beginRun(engine, "online", gameSession.sessionId);
    } catch (error) {
      toast({ title: t("Minesweeper online session unavailable"), description: t(getErrorMessage(error)), tone: "error" });
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
      toast({ title: t("Minesweeper score verified"), description: `${t("Score")} ${formatNumber(result.score)} / +${formatNumber(result.expAwarded)} EXP`, tone: "success" });
      void queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
      void queryClient.invalidateQueries({ queryKey: ["games"] });
      void queryClient.invalidateQueries({ queryKey: ["profile"] });
    } catch (error) {
      setVerification("rejected");
      toast({ title: t("Minesweeper verification failed"), description: t(getErrorMessage(error)), tone: "error" });
    }
  }, [formatNumber, queryClient, t, toast]);

  const finish = useCallback((state: MinesweeperState) => {
    const run = activeRun.current;
    setRunStatus("over");
    setFlagMode(false);
    if (state.score > best) {
      setBest(state.score);
      window.localStorage.setItem(BEST_SCORE_KEY, String(state.score));
    }
    if (run?.mode === "online") void submitOnlineResult(run.token);
  }, [best, submitOnlineResult]);

  const reveal = useCallback((index: number) => {
    const engine = engineRef.current;
    const run = activeRun.current;
    if (!engine || !run || runStatus !== "playing") return;
    if (flagMode) {
      setGameState(engine.toggleFlag(index));
      return;
    }
    const result = engine.reveal(index);
    if (!result.changed) return;
    if (run.mode === "online") run.actions.push(`R:${index}`);
    setGameState(result.state);
    if (engine.terminal()) finish(result.state);
  }, [finish, flagMode, runStatus]);

  const flaggedCount = gameState.cells.filter((cell) => cell.flagged).length;
  const terminalHeading = gameState.status === "won" ? t("Minefield cleared") : t("Mine triggered");

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,620px)_240px] xl:justify-center">
      <div>
        <div className="mb-3 grid grid-cols-4 gap-px border border-[var(--line)] bg-[var(--line)]">
          {[["Score", formatNumber(gameState.score)], ["Best", formatNumber(best)], ["Moves", formatNumber(gameState.moves)], ["Channel", t(mode)]].map(([label, value]) => (
            <dl className="bg-[var(--surface)] p-3" key={label}><dt className="font-telemetry text-[8px] text-[var(--muted)]">{t(label)}</dt><dd className="mt-1 truncate font-mono text-sm font-bold uppercase">{value}</dd></dl>
          ))}
        </div>
        <div className="relative aspect-square border border-[var(--line-strong)] bg-[var(--line)] p-2 sm:p-4">
          <div className="grid h-full grid-cols-9 gap-1" role="grid" aria-label={t("Minesweeper board")}>
            {gameState.cells.map((cell, index) => {
              const row = Math.floor(index / MINESWEEPER_COLUMNS) + 1;
              const column = (index % MINESWEEPER_COLUMNS) + 1;
              const content = cell.flagged ? <Flag size={16} aria-hidden="true" /> : cell.revealed && cell.adjacent === -1 ? <Bomb size={17} aria-hidden="true" /> : cell.revealed ? cell.adjacent || "·" : "";
              return (
                <button
                  key={index}
                  type="button"
                  role="gridcell"
                  className={`grid min-h-0 place-items-center border font-mono text-xs font-black sm:text-base ${cell.revealed ? cell.adjacent === -1 ? "border-[var(--accent)] bg-[var(--accent)] text-white" : "border-[var(--line)] bg-[var(--background)] text-[var(--foreground)]" : "border-[var(--line-strong)] bg-[var(--surface-strong)] hover:border-[var(--accent)]"}`}
                  aria-label={t("Minesweeper row {row}, column {column}: {state}", { row, column, state: cell.flagged ? t("flagged") : cell.revealed ? cell.adjacent === -1 ? t("mine") : t("revealed") : t("hidden") })}
                  disabled={runStatus !== "playing"}
                  onClick={() => reveal(index)}
                  onContextMenu={(event) => { event.preventDefault(); if (runStatus === "playing") setGameState(engineRef.current?.toggleFlag(index) ?? gameState); }}
                >{content}</button>
              );
            })}
          </div>
          {runStatus !== "playing" ? (
            <div className="absolute inset-5 grid place-items-center border border-[var(--line-strong)] bg-[var(--surface)]/95 p-6 text-center">
              <div className="max-w-md"><p className="font-telemetry text-[9px] text-[var(--accent)]">{runStatus === "ready" ? t("[ MINEFIELD / READY ]") : verification === "accepted" ? t("[ SCORE VERIFIED ]") : t("[ FIELD REPORT ]")}</p><h3 className="mt-3 text-4xl font-black uppercase tracking-[-0.055em]">{runStatus === "ready" ? t("Minesweeper") : terminalHeading}</h3><p className="mt-3 text-xs leading-5 text-[var(--muted)]">{t("Reveal every safe cell. Your first move is always protected.")}</p><div className="mt-6 flex flex-wrap justify-center gap-2"><Button onClick={startOffline}>{runStatus === "over" ? <RotateCw size={14} aria-hidden="true" /> : <ScanSearch size={14} aria-hidden="true" />}{runStatus === "over" ? t("Retry offline") : t("Play offline")}</Button>{session.data ? <Button variant="secondary" busy={starting || verification === "submitting"} onClick={() => void startOnline()}><ShieldCheck size={14} aria-hidden="true" />{runStatus === "over" ? t("Retry online") : t("Play online")}</Button> : <Link href="/login" className={buttonStyles("secondary")}><ShieldCheck size={14} aria-hidden="true" />{t("Sign in for online rank")}</Link>}</div></div>
            </div>
          ) : null}
        </div>
      </div>
      <aside className="border border-[var(--line)] bg-[var(--surface)] p-4">
        <p className="font-telemetry text-[9px] text-[var(--muted)]">{t("[ FIELD TOOLS ]")}</p>
        <Button className="mt-6 w-full" variant={flagMode ? "primary" : "secondary"} onClick={() => setFlagMode((value) => !value)} disabled={runStatus !== "playing"}><Flag size={15} aria-hidden="true" />{t(flagMode ? "Reveal mode" : "Flag mode")}</Button>
        <p className="mt-5 text-xs leading-5 text-[var(--muted)]">{t("Click or tap to reveal. Right-click, or enable Flag mode on touch screens, to mark a suspected mine.")}</p>
        <dl className="font-telemetry mt-7 grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]"><div className="flex justify-between bg-[var(--background)] p-3"><dt>{t("Flags")}</dt><dd>{flaggedCount}/{gameState.mineCount}</dd></div><div className="flex justify-between bg-[var(--background)] p-3"><dt>{t("Safe cells")}</dt><dd>{gameState.revealedCount}/{gameState.cells.length - gameState.mineCount}</dd></div></dl>
        <p className="font-telemetry mt-6 border-t border-[var(--line)] pt-4 text-[8px] leading-5 text-[var(--muted)]">{mode === "online" ? t("SERVER-SEED / REPLAY VERIFIED") : t("LOCAL PRACTICE / NO RANK")}</p>
      </aside>
    </div>
  );
}
