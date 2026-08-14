"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { ArrowLeft, ArrowRight, BrickWall, RotateCw, ShieldCheck } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button, buttonStyles } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useSession } from "@/features/auth/hooks";
import { gameResultsApi } from "@/features/games/game-results-api";
import {
  BreakoutEngine,
  BREAKOUT_BALL_RADIUS,
  BREAKOUT_BRICK_COLUMNS,
  BREAKOUT_BRICK_ROWS,
  BREAKOUT_HEIGHT,
  BREAKOUT_PADDLE_HEIGHT,
  BREAKOUT_PADDLE_WIDTH,
  BREAKOUT_PADDLE_Y,
  BREAKOUT_TICK_MS,
  BREAKOUT_WIDTH,
  breakoutBrickRect,
  sameBreakoutState,
  type BreakoutDirection,
  type BreakoutState,
} from "@/games/breakout/engine";
import { randomSeed } from "@/games/core/seeded-random";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";

const BEST_SCORE_KEY = "gameio.breakout.best";

type RunMode = "offline" | "online";
type RunStatus = "ready" | "playing" | "over";
type VerificationStatus = "idle" | "submitting" | "accepted" | "rejected";
type ActiveRun = {
  token: string;
  mode: RunMode;
  sessionId?: string;
  actions: string[];
  startedAt: number;
  submitted: boolean;
};

function drawBreakout(canvas: HTMLCanvasElement, state: BreakoutState) {
  const context = canvas.getContext("2d");
  if (!context) return;
  context.clearRect(0, 0, BREAKOUT_WIDTH, BREAKOUT_HEIGHT);
  context.fillStyle = "#0d0d0d";
  context.fillRect(0, 0, BREAKOUT_WIDTH, BREAKOUT_HEIGHT);

  context.strokeStyle = "rgba(241, 237, 229, 0.08)";
  context.lineWidth = 1;
  for (let x = 0; x <= BREAKOUT_WIDTH; x += 40) {
    context.beginPath();
    context.moveTo(x + 0.5, 0);
    context.lineTo(x + 0.5, BREAKOUT_HEIGHT);
    context.stroke();
  }
  for (let y = 0; y <= BREAKOUT_HEIGHT; y += 40) {
    context.beginPath();
    context.moveTo(0, y + 0.5);
    context.lineTo(BREAKOUT_WIDTH, y + 0.5);
    context.stroke();
  }

  state.bricks.forEach((alive, index) => {
    if (!alive) return;
    const brick = breakoutBrickRect(index);
    const row = Math.floor(index / BREAKOUT_BRICK_COLUMNS);
    context.fillStyle = row === 0 ? "#ed1c24" : row === 1 ? "#f1ede5" : "#85817a";
    context.fillRect(brick.x, brick.y, brick.width, brick.height);
    context.strokeStyle = "#0d0d0d";
    context.strokeRect(brick.x + 0.5, brick.y + 0.5, brick.width - 1, brick.height - 1);
  });

  context.fillStyle = "#f1ede5";
  context.fillRect(
    state.paddleX,
    BREAKOUT_PADDLE_Y,
    BREAKOUT_PADDLE_WIDTH,
    BREAKOUT_PADDLE_HEIGHT,
  );
  context.fillStyle = "#ed1c24";
  context.beginPath();
  context.arc(state.ballX, state.ballY, BREAKOUT_BALL_RADIUS, 0, Math.PI * 2);
  context.fill();
  context.strokeStyle = "#f1ede5";
  context.lineWidth = 2;
  context.strokeRect(1, 1, BREAKOUT_WIDTH - 2, BREAKOUT_HEIGHT - 2);
}

function isTypingTarget(target: EventTarget | null) {
  return (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target instanceof HTMLSelectElement ||
    (target instanceof HTMLElement && target.isContentEditable)
  );
}

export default function BreakoutGame() {
  const session = useSession();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { t, formatNumber } = useI18n();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const engineRef = useRef<BreakoutEngine | null>(null);
  const activeRun = useRef<ActiveRun | null>(null);
  const actionGroup = useRef("");
  const direction = useRef<BreakoutDirection>("N");
  const startingRef = useRef(false);
  const [gameState, setGameState] = useState(() => new BreakoutEngine(1).state());
  const [runStatus, setRunStatus] = useState<RunStatus>("ready");
  const [mode, setMode] = useState<RunMode>("offline");
  const [starting, setStarting] = useState(false);
  const [verification, setVerification] = useState<VerificationStatus>("idle");
  const [best, setBest] = useState(() => {
    if (typeof window === "undefined") return 0;
    const stored = Number(window.localStorage.getItem(BEST_SCORE_KEY));
    return Number.isFinite(stored) && stored > 0 ? stored : 0;
  });

  const beginRun = useCallback(
    (engine: BreakoutEngine, runMode: RunMode, sessionId?: string) => {
      const initialState = engine.state();
      engineRef.current = engine;
      direction.current = "N";
      actionGroup.current = "";
      activeRun.current = {
        token: `${runMode}-${Date.now()}-${initialState.ballX}`,
        mode: runMode,
        sessionId,
        actions: [],
        startedAt: Date.now(),
        submitted: false,
      };
      setGameState(initialState);
      setMode(runMode);
      setVerification("idle");
      setRunStatus("playing");
    },
    [],
  );

  const startOffline = useCallback(() => {
    beginRun(new BreakoutEngine(randomSeed()), "offline");
  }, [beginRun]);

  const startOnline = useCallback(async () => {
    if (!session.data || startingRef.current) return;
    startingRef.current = true;
    setStarting(true);
    try {
      const gameSession = await gameResultsApi.createSession("breakout");
      const engine = new BreakoutEngine(gameSession.seed);
      if (!sameBreakoutState(engine.state(), gameSession.initialState)) {
        throw new Error("The server returned an inconsistent seeded Breakout state.");
      }
      beginRun(engine, "online", gameSession.sessionId);
    } catch (error) {
      toast({
        title: t("Breakout online session unavailable"),
        description: t(getErrorMessage(error)),
        tone: "error",
      });
    } finally {
      startingRef.current = false;
      setStarting(false);
    }
  }, [beginRun, session.data, t, toast]);

  const submitOnlineResult = useCallback(
    async (runToken: string) => {
      const run = activeRun.current;
      if (
        !run ||
        run.token !== runToken ||
        run.mode !== "online" ||
        !run.sessionId ||
        run.submitted
      ) {
        return;
      }
      run.submitted = true;
      setVerification("submitting");
      try {
        const result = await gameResultsApi.complete({
          sessionId: run.sessionId,
          actions: [...run.actions],
          durationSeconds: Math.max(1, Math.ceil((Date.now() - run.startedAt) / 1000)),
        });
        setVerification("accepted");
        toast({
          title: t("Breakout score verified"),
          description: `${t("Score")} ${formatNumber(result.score)} / +${formatNumber(result.expAwarded)} EXP`,
          tone: "success",
        });
        void queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
        void queryClient.invalidateQueries({ queryKey: ["games"] });
        void queryClient.invalidateQueries({ queryKey: ["profile"] });
      } catch (error) {
        setVerification("rejected");
        toast({
          title: t("Breakout verification failed"),
          description: t(getErrorMessage(error)),
          tone: "error",
        });
      }
    },
    [formatNumber, queryClient, t, toast],
  );

  useEffect(() => {
    if (runStatus !== "playing" && canvasRef.current) {
      drawBreakout(canvasRef.current, gameState);
    }
  }, [gameState, runStatus]);

  useEffect(() => {
    if (runStatus !== "playing") return;
    let animationFrame = 0;
    let previousTime: number | null = null;
    let accumulator = 0;
    let renderedTick = engineRef.current?.state().tick ?? 0;

    const frame = (time: number) => {
      const engine = engineRef.current;
      const run = activeRun.current;
      if (!engine || !run) return;
      if (previousTime === null) previousTime = time;
      const delta = Math.min(100, time - previousTime);
      previousTime = time;
      accumulator += delta;
      let state = engine.state();
      while (accumulator >= BREAKOUT_TICK_MS && !engine.terminal()) {
        const action = direction.current;
        state = engine.step(action);
        if (run.mode === "online") {
          actionGroup.current += action;
          if (actionGroup.current.length === 3 || engine.terminal()) {
            run.actions.push(actionGroup.current);
            actionGroup.current = "";
          }
        }
        accumulator -= BREAKOUT_TICK_MS;
      }
      if (canvasRef.current) drawBreakout(canvasRef.current, state);
      if (state.tick - renderedTick >= 4 || engine.terminal()) {
        renderedTick = state.tick;
        setGameState(state);
      }
      if (engine.terminal()) {
        setRunStatus("over");
        direction.current = "N";
        if (state.score > best) {
          setBest(state.score);
          window.localStorage.setItem(BEST_SCORE_KEY, String(state.score));
        }
        if (run.mode === "online") void submitOnlineResult(run.token);
        return;
      }
      animationFrame = window.requestAnimationFrame(frame);
    };
    animationFrame = window.requestAnimationFrame(frame);
    return () => window.cancelAnimationFrame(animationFrame);
  }, [best, runStatus, submitOnlineResult]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (runStatus !== "playing" || isTypingTarget(event.target)) return;
      if (["ArrowLeft", "a", "A"].includes(event.key)) {
        event.preventDefault();
        direction.current = "L";
      } else if (["ArrowRight", "d", "D"].includes(event.key)) {
        event.preventDefault();
        direction.current = "R";
      }
    };
    const onKeyUp = (event: KeyboardEvent) => {
      if (runStatus !== "playing" || isTypingTarget(event.target)) return;
      if (["ArrowLeft", "ArrowRight", "a", "A", "d", "D"].includes(event.key)) {
        event.preventDefault();
        direction.current = "N";
      }
    };
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
    };
  }, [runStatus]);

  const terminalHeading = gameState.status === "won" ? t("Wall cleared") : t("Run over");
  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,760px)_240px] xl:justify-center">
      <div>
        <div className="mb-3 grid grid-cols-4 gap-px border border-[var(--line)] bg-[var(--line)]">
          {[
            ["Score", formatNumber(gameState.score)],
            ["Best", formatNumber(best)],
            ["Lives", formatNumber(gameState.lives)],
            ["Channel", t(mode)],
          ].map(([label, value]) => (
            <dl className="bg-[var(--surface)] p-3" key={label}>
              <dt className="font-telemetry text-[8px] text-[var(--muted)]">{t(label)}</dt>
              <dd className="mt-1 truncate font-mono text-sm font-bold uppercase">{value}</dd>
            </dl>
          ))}
        </div>
        <div className="relative overflow-hidden border border-[var(--line-strong)] bg-black">
          <canvas
            ref={canvasRef}
            width={BREAKOUT_WIDTH}
            height={BREAKOUT_HEIGHT}
            className="block aspect-[4/3] w-full touch-none"
            aria-label={t("Breakout play field")}
            onPointerDown={(event) => {
              if (runStatus !== "playing") return;
              const rect = event.currentTarget.getBoundingClientRect();
              direction.current = event.clientX - rect.left < rect.width / 2 ? "L" : "R";
              event.currentTarget.setPointerCapture(event.pointerId);
            }}
            onPointerUp={() => (direction.current = "N")}
            onPointerCancel={() => (direction.current = "N")}
          >
            {t("Breakout requires canvas support.")}
          </canvas>
          {runStatus !== "playing" ? (
            <div className="absolute inset-5 grid place-items-center border border-white/20 bg-black/90 p-6 text-center">
              <div className="max-w-md">
                <p className="font-telemetry text-[9px] text-[var(--accent)]">
                  {runStatus === "ready" ? t("[ BREAKOUT ENGINE / READY ]") : verification === "accepted" ? t("[ SCORE VERIFIED ]") : t("[ WALL REPORT ]")}
                </p>
                <h3 className="mt-3 text-4xl font-black uppercase tracking-[-0.055em] text-white sm:text-5xl">
                  {runStatus === "ready" ? t("Breakout") : terminalHeading}
                </h3>
                <p className="mt-3 text-xs leading-5 text-zinc-400">
                  {t("Move the paddle, keep the ball alive, and clear every brick.")}
                </p>
                <div className="mt-6 flex flex-wrap justify-center gap-2">
                  <Button onClick={startOffline}>
                    {runStatus === "over" ? <RotateCw size={14} aria-hidden="true" /> : <BrickWall size={14} aria-hidden="true" />}
                    {runStatus === "over" ? t("Retry offline") : t("Play offline")}
                  </Button>
                  {session.data ? (
                    <Button variant="secondary" busy={starting || verification === "submitting"} onClick={() => void startOnline()}>
                      <ShieldCheck size={14} aria-hidden="true" />
                      {runStatus === "over" ? t("Retry online") : t("Play online")}
                    </Button>
                  ) : (
                    <Link href="/login" className={buttonStyles("secondary")}>
                      <ShieldCheck size={14} aria-hidden="true" />
                      {t("Sign in for online rank")}
                    </Link>
                  )}
                </div>
              </div>
            </div>
          ) : null}
        </div>
      </div>
      <aside className="border border-[var(--line)] bg-[var(--surface)] p-4">
        <p className="font-telemetry text-[9px] text-[var(--muted)]">{t("[ PADDLE CONTROL ]")}</p>
        <div className="mt-6 grid grid-cols-2 gap-2">
          <Button compact variant="secondary" aria-label={t("Move left")} onPointerDown={() => (direction.current = "L")} onPointerUp={() => (direction.current = "N")} onPointerLeave={() => (direction.current = "N")} disabled={runStatus !== "playing"}>
            <ArrowLeft size={18} aria-hidden="true" />
          </Button>
          <Button compact variant="secondary" aria-label={t("Move right")} onPointerDown={() => (direction.current = "R")} onPointerUp={() => (direction.current = "N")} onPointerLeave={() => (direction.current = "N")} disabled={runStatus !== "playing"}>
            <ArrowRight size={18} aria-hidden="true" />
          </Button>
        </div>
        <p className="mt-6 text-xs leading-5 text-[var(--muted)]">{t("Keyboard: Left/Right or A/D. Touch either half of the arena or use the paddle controls.")}</p>
        <dl className="font-telemetry mt-7 grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]">
          <div className="flex justify-between bg-[var(--background)] p-3"><dt>{t("Bricks")}</dt><dd>{gameState.bricks.filter(Boolean).length}/{BREAKOUT_BRICK_COLUMNS * BREAKOUT_BRICK_ROWS}</dd></div>
          <div className="flex justify-between bg-[var(--background)] p-3"><dt>{t("Frame rate")}</dt><dd>60 FPS</dd></div>
        </dl>
        <p className="font-telemetry mt-6 border-t border-[var(--line)] pt-4 text-[8px] leading-5 text-[var(--muted)]">{mode === "online" ? t("SERVER-SEED / REPLAY VERIFIED") : t("LOCAL PRACTICE / NO RANK")}</p>
      </aside>
    </div>
  );
}
