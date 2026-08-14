"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { Bird, RotateCw, ShieldCheck, Wifi, WifiOff } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button, buttonStyles } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useSession } from "@/features/auth/hooks";
import { gameResultsApi } from "@/features/games/game-results-api";
import {
  birdYInPixels,
  FlappyEngine,
  FLAPPY_BIRD_HALF_HEIGHT,
  FLAPPY_BIRD_HALF_WIDTH,
  FLAPPY_PIPE_GAP,
  FLAPPY_PIPE_WIDTH,
  FLAPPY_TICK_MS,
  projectFlappyState,
  sameFlappyState,
  type FlappyAction,
  type FlappyState,
} from "@/games/flappy-bird/engine";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";
import { movementKeyAllowed, shouldPresentFrame } from "@/features/settings/input-preferences";

const BEST_SCORE_KEY = "gameio.flappy-bird.best";

type RunMode = "offline" | "online";
type RunStatus = "ready" | "playing" | "over";
type VerificationStatus = "idle" | "submitting" | "accepted" | "rejected";

type ActiveRun = {
  token: string;
  mode: RunMode;
  sessionId?: string;
  actions: FlappyAction[];
  startedAt: number;
  submitted: boolean;
};

function randomSeed() {
  const values = new Uint32Array(1);
  window.crypto.getRandomValues(values);
  return values[0] || 1;
}

function isTypingTarget(target: EventTarget | null) {
  return (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target instanceof HTMLSelectElement ||
    (target instanceof HTMLElement && target.isContentEditable)
  );
}

function isFlapKey(event: KeyboardEvent | React.KeyboardEvent) {
  return (
    event.code === "Space" ||
    ((event.key === "ArrowUp" || event.key.toLowerCase() === "w") &&
      movementKeyAllowed(event.key))
  );
}

function drawGrid(context: CanvasRenderingContext2D, state: FlappyState) {
  context.strokeStyle = "rgba(241, 237, 229, 0.08)";
  context.lineWidth = 1;
  for (let x = 0; x <= state.width; x += 40) {
    context.beginPath();
    context.moveTo(x + 0.5, 0);
    context.lineTo(x + 0.5, state.height);
    context.stroke();
  }
  for (let y = 0; y <= state.height; y += 40) {
    context.beginPath();
    context.moveTo(0, y + 0.5);
    context.lineTo(state.width, y + 0.5);
    context.stroke();
  }
}

function drawPipe(
  context: CanvasRenderingContext2D,
  x: number,
  gapCenter: number,
  passed: boolean,
  height: number,
) {
  const gapTop = gapCenter - FLAPPY_PIPE_GAP / 2;
  const gapBottom = gapCenter + FLAPPY_PIPE_GAP / 2;
  const capInset = 7;
  const capHeight = 14;

  context.fillStyle = passed ? "#77736d" : "#d8d3c9";
  context.strokeStyle = "#f1ede5";
  context.lineWidth = 2;
  context.fillRect(x, 0, FLAPPY_PIPE_WIDTH, gapTop - capHeight);
  context.strokeRect(x, -1, FLAPPY_PIPE_WIDTH, gapTop - capHeight + 1);
  context.fillRect(
    x - capInset,
    gapTop - capHeight,
    FLAPPY_PIPE_WIDTH + capInset * 2,
    capHeight,
  );
  context.strokeRect(
    x - capInset,
    gapTop - capHeight,
    FLAPPY_PIPE_WIDTH + capInset * 2,
    capHeight,
  );
  context.fillRect(
    x - capInset,
    gapBottom,
    FLAPPY_PIPE_WIDTH + capInset * 2,
    capHeight,
  );
  context.strokeRect(
    x - capInset,
    gapBottom,
    FLAPPY_PIPE_WIDTH + capInset * 2,
    capHeight,
  );
  context.fillRect(
    x,
    gapBottom + capHeight,
    FLAPPY_PIPE_WIDTH,
    height - gapBottom - capHeight,
  );
  context.strokeRect(
    x,
    gapBottom + capHeight,
    FLAPPY_PIPE_WIDTH,
    height - gapBottom - capHeight + 1,
  );

  context.strokeStyle = "rgba(13, 13, 13, 0.35)";
  context.lineWidth = 1;
  for (let stripe = x + 14; stripe < x + FLAPPY_PIPE_WIDTH; stripe += 18) {
    context.beginPath();
    context.moveTo(stripe, 0);
    context.lineTo(stripe, Math.max(0, gapTop - capHeight));
    context.moveTo(stripe, gapBottom + capHeight);
    context.lineTo(stripe, height);
    context.stroke();
  }
}

function drawBird(context: CanvasRenderingContext2D, state: FlappyState) {
  const birdY = birdYInPixels(state);
  const rotation = Math.max(-0.38, Math.min(0.5, state.birdVelocity / 1_800));
  context.save();
  context.translate(state.birdX, birdY);
  context.rotate(rotation);

  context.fillStyle = "#f1ede5";
  context.strokeStyle = "#0d0d0d";
  context.lineWidth = 2;
  context.beginPath();
  context.ellipse(
    0,
    0,
    FLAPPY_BIRD_HALF_WIDTH,
    FLAPPY_BIRD_HALF_HEIGHT,
    0,
    0,
    Math.PI * 2,
  );
  context.fill();
  context.stroke();

  context.fillStyle = "#85817a";
  context.beginPath();
  context.moveTo(-6, 0);
  context.lineTo(-23, -16);
  context.lineTo(-13, 9);
  context.closePath();
  context.fill();
  context.stroke();

  context.fillStyle = "#ed1c24";
  context.beginPath();
  context.arc(7, -4, 2.8, 0, Math.PI * 2);
  context.fill();

  context.fillStyle = "#d8d3c9";
  context.beginPath();
  context.moveTo(14, -3);
  context.lineTo(25, 1);
  context.lineTo(14, 5);
  context.closePath();
  context.fill();
  context.stroke();
  context.restore();
}

function drawScene(canvas: HTMLCanvasElement, state: FlappyState) {
  const context = canvas.getContext("2d");
  if (!context) return;
  context.clearRect(0, 0, state.width, state.height);
  context.fillStyle = "#0d0d0d";
  context.fillRect(0, 0, state.width, state.height);
  drawGrid(context, state);

  context.strokeStyle = "rgba(237, 28, 36, 0.42)";
  context.setLineDash([5, 7]);
  context.beginPath();
  context.moveTo(state.birdX, 0);
  context.lineTo(state.birdX, state.height);
  context.stroke();
  context.setLineDash([]);

  state.pipes.forEach((pipe) =>
    drawPipe(context, pipe.x, pipe.gapCenter, pipe.passed, state.height),
  );
  drawBird(context, state);

  context.strokeStyle = "#4a4a4a";
  context.lineWidth = 2;
  context.strokeRect(1, 1, state.width - 2, state.height - 2);
}

export default function FlappyBirdGame() {
  const session = useSession();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { t, formatNumber } = useI18n();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const engineRef = useRef<FlappyEngine | null>(null);
  const activeRun = useRef<ActiveRun | null>(null);
  const pendingFlap = useRef(false);
  const startingRef = useRef(false);
  const [gameState, setGameState] = useState(() =>
    new FlappyEngine(1).state(),
  );
  const [status, setStatus] = useState<RunStatus>("ready");
  const [mode, setMode] = useState<RunMode>("offline");
  const [runId, setRunId] = useState(0);
  const [starting, setStarting] = useState(false);
  const [verification, setVerification] =
    useState<VerificationStatus>("idle");
  const [best, setBest] = useState(() => {
    if (typeof window === "undefined") return 0;
    const stored = Number(window.localStorage.getItem(BEST_SCORE_KEY));
    return Number.isFinite(stored) && stored > 0 ? stored : 0;
  });

  useEffect(() => {
    if (status !== "playing" && canvasRef.current) {
      drawScene(canvasRef.current, gameState);
    }
  }, [gameState, status]);

  const beginRun = useCallback(
    (engine: FlappyEngine, runMode: RunMode, sessionId?: string) => {
      const initialState = engine.state();
      engineRef.current = engine;
      pendingFlap.current = false;
      activeRun.current = {
        token: `${runMode}-${Date.now()}-${initialState.tick}`,
        mode: runMode,
        sessionId,
        actions: [],
        startedAt: Date.now(),
        submitted: false,
      };
      setGameState(initialState);
      setMode(runMode);
      setVerification("idle");
      setStatus("playing");
      setRunId((current) => current + 1);
    },
    [],
  );

  const startOffline = useCallback(() => {
    beginRun(new FlappyEngine(randomSeed()), "offline");
  }, [beginRun]);

  const startOnline = useCallback(async () => {
    if (!session.data || startingRef.current) return;
    startingRef.current = true;
    setStarting(true);
    try {
      const gameSession = await gameResultsApi.createSession("flappy-bird");
      const engine = new FlappyEngine(gameSession.seed);
      if (!sameFlappyState(engine.state(), gameSession.initialState)) {
        throw new Error(
          "The server returned an inconsistent seeded Flappy Bird state.",
        );
      }
      beginRun(engine, "online", gameSession.sessionId);
    } catch (error) {
      toast({
        title: t("Flappy Bird online session unavailable"),
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
      const sessionId = run.sessionId;
      const actions = [...run.actions];
      const durationSeconds = Math.max(
        1,
        Math.ceil((Date.now() - run.startedAt) / 1_000),
      );
      setVerification("submitting");
      try {
        const result = await gameResultsApi.complete({
          sessionId,
          actions,
          durationSeconds,
        });
        if (activeRun.current?.token === runToken) {
          setVerification("accepted");
        }
        toast({
          title: t("Flappy Bird score verified"),
          description:
            t("Score") +
            " " +
            formatNumber(result.score) +
            " / +" +
            formatNumber(result.expAwarded) +
            " EXP / " +
            t("level") +
            " " +
            result.resultingLevel,
          tone: "success",
        });
        void queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
        void queryClient.invalidateQueries({ queryKey: ["games"] });
        void queryClient.invalidateQueries({ queryKey: ["games", "recent"] });
        void queryClient.invalidateQueries({ queryKey: ["profile"] });
      } catch (error) {
        if (activeRun.current?.token === runToken) {
          setVerification("rejected");
        }
        toast({
          title: t("Flappy Bird verification failed"),
          description: t(getErrorMessage(error)),
          tone: "error",
        });
      }
    },
    [formatNumber, queryClient, t, toast],
  );

  useEffect(() => {
    if (status !== "playing") return;
    let animationFrame = 0;
    let previousTime: number | null = null;
    let accumulator = 0;
    let stopped = false;
    let latestState = engineRef.current?.state() ?? null;
    let previousPresentation = 0;

    const resetClock = () => {
      previousTime = null;
      accumulator = 0;
    };
    const frame = (time: number) => {
      if (previousTime === null) {
        previousTime = time;
      } else {
        accumulator += Math.min(250, Math.max(0, time - previousTime));
        previousTime = time;
      }

      while (accumulator >= FLAPPY_TICK_MS && !stopped) {
        const engine = engineRef.current;
        const run = activeRun.current;
        if (!engine || !run) {
          stopped = true;
          break;
        }
        const action: FlappyAction = pendingFlap.current ? "FLAP" : "WAIT";
        pendingFlap.current = false;
        const nextState = engine.step(action);
        latestState = nextState;
        if (run.mode === "online") run.actions.push(action);
        setGameState(nextState);
        setBest((currentBest) => {
          if (nextState.score <= currentBest) return currentBest;
          window.localStorage.setItem(BEST_SCORE_KEY, String(nextState.score));
          return nextState.score;
        });
        accumulator -= FLAPPY_TICK_MS;
        if (nextState.status === "over") {
          stopped = true;
          setStatus("over");
          if (run.mode === "online") {
            void submitOnlineResult(run.token);
          }
        }
      }

      const canvas = canvasRef.current;
      if (
        canvas &&
        latestState &&
        shouldPresentFrame(time, previousPresentation)
      ) {
        previousPresentation = time;
        const renderState = stopped
          ? latestState
          : projectFlappyState(
              latestState,
              pendingFlap.current ? "FLAP" : "WAIT",
              accumulator / FLAPPY_TICK_MS,
            );
        drawScene(canvas, renderState);
      }

      if (!stopped) animationFrame = window.requestAnimationFrame(frame);
    };

    document.addEventListener("visibilitychange", resetClock);
    animationFrame = window.requestAnimationFrame(frame);
    return () => {
      stopped = true;
      document.removeEventListener("visibilitychange", resetClock);
      window.cancelAnimationFrame(animationFrame);
    };
  }, [runId, status, submitOnlineResult]);

  const flap = useCallback(() => {
    if (status === "playing") pendingFlap.current = true;
  }, [status]);

  useEffect(() => {
    if (status !== "playing") return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (
        event.defaultPrevented ||
        isTypingTarget(event.target) ||
        !isFlapKey(event)
      ) {
        return;
      }
      event.preventDefault();
      flap();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [flap, status]);

  const restart = () => {
    if (mode === "online" && session.data) {
      void startOnline();
    } else {
      startOffline();
    }
  };

  const actionLocked =
    starting || session.isLoading || verification === "submitting";

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,760px)_240px] xl:justify-center">
      <div>
        <div className="mb-3 grid grid-cols-2 gap-px border border-[var(--line)] bg-[var(--line)] sm:grid-cols-4">
          {[
            ["Score", gameState.score],
            ["Best", best],
            ["State", status],
            ["Channel", mode],
          ].map(([label, value]) => (
            <dl className="min-w-0 bg-[var(--surface)] p-3" key={label}>
              <dt className="font-telemetry text-[8px] text-[var(--muted)]">
                {t(String(label))}
              </dt>
              <dd className="mt-1 truncate font-mono text-sm font-bold uppercase">
                {typeof value === "number"
                  ? formatNumber(value)
                  : t(String(value))}
              </dd>
            </dl>
          ))}
        </div>

        <div
          className="relative aspect-[4/3] touch-none overflow-hidden border border-[var(--line-strong)] bg-[#0d0d0d]"
          role="application"
          aria-label={t("Flappy Bird flight area")}
          aria-describedby="flappy-controls-help"
          tabIndex={0}
          onKeyDown={(event) => {
            if (
              status !== "playing" ||
              (!isFlapKey(event) && event.key !== "Enter")
            ) {
              return;
            }
            event.preventDefault();
            flap();
          }}
          onPointerDown={(event) => {
            if (status !== "playing") return;
            event.preventDefault();
            flap();
          }}
        >
          <canvas
            ref={canvasRef}
            width={gameState.width}
            height={gameState.height}
            className="block h-full w-full"
          >
            {t("Flappy Bird requires canvas support.")}
          </canvas>

          {status !== "playing" ? (
            <div className="absolute inset-2 grid place-items-center border border-[#4a4a4a] bg-[#111111]/95 p-3 text-center text-[#eeeae3] sm:inset-5 sm:p-8">
              <div className="max-w-lg">
                <p className="font-telemetry text-[9px] text-[var(--accent)]">
                  {status === "ready"
                    ? t("[ FLIGHT ENGINE / READY ]")
                    : verification === "accepted"
                      ? t("[ SCORE VERIFIED ]")
                      : verification === "submitting"
                        ? t("[ VERIFYING FLIGHT ]")
                        : verification === "rejected"
                          ? t("[ VERIFICATION FAILED ]")
                          : t("[ IMPACT DETECTED ]")}
                </p>
                <h3 className="mt-2 text-3xl font-black uppercase tracking-[-0.055em] sm:mt-3 sm:text-5xl">
                  {status === "ready" ? "Flappy Bird" : t("Flight ended")}
                </h3>
                <p className="mx-auto mt-2 line-clamp-2 max-w-md text-xs leading-5 text-[#a8a39c] sm:mt-3 sm:line-clamp-none">
                  {status === "ready"
                    ? t("Tap, click, or press Space to hold altitude through each gate.")
                    : verification === "accepted"
                      ? t("The server reproduced the flight and recorded its score.")
                      : verification === "submitting"
                        ? t("The server is replaying every fixed simulation tick.")
                        : verification === "rejected"
                          ? t("This flight was not added to the online ranking.")
                          : t("Clear another route and push the local best higher.")}
                </p>
                <div className="mt-4 flex flex-wrap justify-center gap-2 sm:mt-6 sm:gap-3">
                  {session.data ? (
                    <Button
                      onClick={() => void startOnline()}
                      busy={starting}
                      disabled={actionLocked}
                    >
                      <Wifi size={14} aria-hidden="true" />
                      {t(status === "ready" ? "Play online" : "Retry online")}
                    </Button>
                  ) : null}
                  <Button
                    variant={session.data ? "secondary" : "primary"}
                    onClick={startOffline}
                    disabled={verification === "submitting"}
                  >
                    <WifiOff size={14} aria-hidden="true" />
                    {t(status === "ready" ? "Play offline" : "Retry offline")}
                  </Button>
                  {!session.data && !session.isLoading ? (
                    <Link href="/login" className={buttonStyles("ghost")}>
                      <ShieldCheck size={14} aria-hidden="true" />
                      {t("Sign in for online rank")}
                    </Link>
                  ) : null}
                </div>
              </div>
            </div>
          ) : null}
        </div>
        <p className="sr-only" aria-live="polite">
          {status === "over"
            ? t("Flight ended with score {score}.", {
                score: formatNumber(gameState.score),
              })
            : ""}
        </p>
      </div>

      <aside className="border border-[var(--line)] bg-[var(--surface)] p-4">
        <p className="font-telemetry text-[9px] text-[var(--muted)]">
          {t("[ ALTITUDE CONTROL ]")}
        </p>
        <Button
          className="mt-6 min-h-16 w-full"
          variant="secondary"
          disabled={status !== "playing"}
          onClick={flap}
        >
          <Bird size={20} aria-hidden="true" />
          {t("Flap")}
        </Button>
        <p
          id="flappy-controls-help"
          className="mt-5 text-xs leading-5 text-[var(--muted)]"
        >
          {t("Keyboard: Space, W, or Arrow Up. Touch/click the flight area or use the Flap button.")}
        </p>

        <div className="font-telemetry mt-6 grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]">
          <div className="flex items-center justify-between bg-[var(--background)] p-3">
            <span>{t("Route")}</span>
            <span>{formatNumber(gameState.tick)} TICKS</span>
          </div>
          <div className="flex items-center justify-between bg-[var(--background)] p-3">
            <span>{t("Signal")}</span>
            <span className={mode === "online" ? "status-online" : "text-[var(--muted)]"}>
              {t(mode)}
            </span>
          </div>
        </div>

        <div className="mt-6 border-t border-[var(--line)] pt-4 text-xs leading-5 text-[var(--muted)]">
          {mode === "online" ? (
            <span className="flex items-start gap-2 text-[var(--foreground)]">
              <ShieldCheck
                size={14}
                className="mt-0.5 shrink-0 text-[var(--accent)]"
                aria-hidden="true"
              />
              {t("Online runs use a server seed and verified replay for ranks and EXP.")}
            </span>
          ) : (
            <span className="flex items-start gap-2">
              <WifiOff size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
              {t("Offline runs stay in this browser and never require an account.")}
            </span>
          )}
        </div>

        {status === "playing" ? (
          <Button
            className="mt-6 w-full"
            variant="ghost"
            compact
            onClick={restart}
            busy={starting}
          >
            <RotateCw size={13} aria-hidden="true" />
            {t("Restart")}
          </Button>
        ) : null}
      </aside>
    </div>
  );
}
