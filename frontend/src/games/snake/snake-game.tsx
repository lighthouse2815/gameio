"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Pause,
  Play,
  RotateCw,
  ShieldCheck,
} from "lucide-react";
import { Button, buttonStyles } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useSession } from "@/features/auth/hooks";
import {
  gameResultsApi,
  type VerifiedSnakeAction,
} from "@/features/games/game-results-api";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";
import {
  cloneSnakeState,
  createSnakeState,
  queueDirection,
  SnakeXorShift32,
  snakeActionForDirection,
  stepSnake,
  type SnakeDirection,
  type SnakeState,
} from "@/games/snake/engine";

const BEST_SCORE_KEY = "gameio.snake.best";

type RunDescriptor = {
  runToken: string;
  seed: number;
  initialState: SnakeState;
  verified: boolean;
};

type VerifiedRun = {
  runToken: string;
  sessionId: string;
  actions: VerifiedSnakeAction[];
  startedAt: number;
  submitted: boolean;
};

function randomSeed() {
  const values = new Uint32Array(1);
  crypto.getRandomValues(values);
  return values[0] || 1;
}

function samePoint(
  left: { x: number; y: number } | null,
  right: { x: number; y: number } | null,
) {
  return (
    left === right ||
    Boolean(left && right && left.x === right.x && left.y === right.y)
  );
}

function matchesSeededInitialState(
  received: SnakeState,
  reconstructed: SnakeState,
) {
  return (
    received.width === reconstructed.width &&
    received.height === reconstructed.height &&
    received.direction === reconstructed.direction &&
    received.queuedDirection === reconstructed.queuedDirection &&
    received.score === reconstructed.score &&
    received.tickMs === reconstructed.tickMs &&
    received.status === reconstructed.status &&
    samePoint(received.food, reconstructed.food) &&
    received.body.length === reconstructed.body.length &&
    received.body.every((point, index) =>
      samePoint(point, reconstructed.body[index]),
    )
  );
}

export default function SnakeGame() {
  const canvasHost = useRef<HTMLDivElement>(null);
  const directionControl = useRef<(direction: SnakeDirection) => void>(() => {});
  const pauseControl = useRef<(paused: boolean) => void>(() => {});
  const runDescriptor = useRef<RunDescriptor | null>(null);
  const verifiedRun = useRef<VerifiedRun | null>(null);
  const startingRef = useRef(false);
  const session = useSession();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { t, formatNumber } = useI18n();
  const [started, setStarted] = useState(false);
  const [runId, setRunId] = useState(0);
  const [status, setStatus] = useState<"ready" | "playing" | "over" | "won">(
    "ready",
  );
  const [paused, setPaused] = useState(false);
  const [score, setScore] = useState(0);
  const [mode, setMode] = useState<"local" | "verified">("local");
  const [starting, setStarting] = useState(false);
  const [verification, setVerification] = useState<
    "idle" | "submitting" | "accepted" | "rejected"
  >("idle");
  const [best, setBest] = useState(() => {
    if (typeof window === "undefined") return 0;
    const stored = Number(window.localStorage.getItem(BEST_SCORE_KEY));
    return Number.isFinite(stored) && stored > 0 ? stored : 0;
  });

  const submitVerifiedResult = useCallback(async (runToken: string) => {
    const run = verifiedRun.current;
    if (!run || run.runToken !== runToken || run.submitted) return;
    run.submitted = true;
    setVerification("submitting");
    try {
      const result = await gameResultsApi.complete({
        sessionId: run.sessionId,
        actions: run.actions,
        durationSeconds: Math.max(
          1,
          Math.ceil((Date.now() - run.startedAt) / 1000),
        ),
      });
      setVerification("accepted");
      toast({
        title: t("Snake replay verified"),
        description:
          t("Score") + " " +
          formatNumber(result.score) +
          " / +" +
          formatNumber(result.expAwarded) +
          " EXP / " + t("level") + " " +
          result.resultingLevel,
        tone: "success",
      });
      void queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
      void queryClient.invalidateQueries({ queryKey: ["games", "recent"] });
      void queryClient.invalidateQueries({ queryKey: ["profile"] });
    } catch (error) {
      setVerification("rejected");
      toast({
        title: t("Snake verification failed"),
        description: t(getErrorMessage(error)),
        tone: "error",
      });
    }
  }, [formatNumber, queryClient, t, toast]);

  useEffect(() => {
    const descriptor = runDescriptor.current;
    if (!started || !canvasHost.current || !descriptor) return;
    const activeDescriptor: RunDescriptor = descriptor;
    const host = canvasHost.current;
    let disposed = false;
    let game: import("phaser").Game | null = null;

    void import("phaser").then((module) => {
      if (disposed) return;
      const Phaser = module;
      const random = new SnakeXorShift32(activeDescriptor.seed);
      createSnakeState(
        activeDescriptor.initialState.width,
        activeDescriptor.initialState.height,
        () => random.next(),
      );

      class SnakeScene extends Phaser.Scene {
        private telemetry!: import("phaser").GameObjects.Graphics;
        private snakeState = cloneSnakeState(activeDescriptor.initialState);
        private lastTick = 0;
        private scenePaused = false;

        constructor() {
          super("snake");
        }

        create() {
          directionControl.current = (direction) => this.change(direction);
          pauseControl.current = (value) => this.setRunPaused(value);
          this.telemetry = this.add.graphics();
          this.input.keyboard?.on("keydown-UP", () => this.change("up"));
          this.input.keyboard?.on("keydown-W", () => this.change("up"));
          this.input.keyboard?.on("keydown-DOWN", () => this.change("down"));
          this.input.keyboard?.on("keydown-S", () => this.change("down"));
          this.input.keyboard?.on("keydown-LEFT", () => this.change("left"));
          this.input.keyboard?.on("keydown-A", () => this.change("left"));
          this.input.keyboard?.on("keydown-RIGHT", () => this.change("right"));
          this.input.keyboard?.on("keydown-D", () => this.change("right"));
          this.drawState();
        }

        update(time: number) {
          if (
            this.scenePaused ||
            this.snakeState.status !== "playing" ||
            time - this.lastTick < this.snakeState.tickMs
          ) {
            return;
          }
          this.lastTick = time;
          const effectiveDirection = this.snakeState.queuedDirection;
          const previousScore = this.snakeState.score;
          this.snakeState = stepSnake(this.snakeState, () => random.next());
          if (
            activeDescriptor.verified &&
            verifiedRun.current?.runToken === activeDescriptor.runToken
          ) {
            verifiedRun.current.actions.push(
              snakeActionForDirection(effectiveDirection),
            );
          }
          if (this.snakeState.score !== previousScore) {
            setScore(this.snakeState.score);
            setBest((currentBest) => {
              const nextBest = Math.max(currentBest, this.snakeState.score);
              window.localStorage.setItem(BEST_SCORE_KEY, String(nextBest));
              return nextBest;
            });
          }
          if (this.snakeState.status !== "playing") {
            setStatus(this.snakeState.status);
            if (activeDescriptor.verified) {
              void submitVerifiedResult(activeDescriptor.runToken);
            }
          }
          this.drawState();
        }

        change(direction: SnakeDirection) {
          this.snakeState = queueDirection(this.snakeState, direction);
        }

        setRunPaused(value: boolean) {
          this.scenePaused = value;
        }

        private drawState() {
          const cell = 32;
          this.telemetry.clear();
          this.telemetry.lineStyle(1, 0x2f2f2f, 0.72);
          for (let x = 0; x <= this.snakeState.width; x += 1) {
            this.telemetry.lineBetween(x * cell, 0, x * cell, 480);
          }
          for (let y = 0; y <= this.snakeState.height; y += 1) {
            this.telemetry.lineBetween(0, y * cell, 640, y * cell);
          }
          if (this.snakeState.food) {
            this.telemetry.fillStyle(0xed1c24, 1);
            this.telemetry.fillRect(
              this.snakeState.food.x * cell + 7,
              this.snakeState.food.y * cell + 7,
              cell - 14,
              cell - 14,
            );
          }
          this.snakeState.body.forEach((segment, index) => {
            this.telemetry.fillStyle(index === 0 ? 0xf1ede5 : 0x85817a, 1);
            this.telemetry.fillRect(
              segment.x * cell + 2,
              segment.y * cell + 2,
              cell - 4,
              cell - 4,
            );
          });
        }
      }

      game = new Phaser.Game({
        type: Phaser.AUTO,
        width: 640,
        height: 480,
        parent: host,
        backgroundColor: "#0d0d0d",
        render: { antialias: false, pixelArt: true },
        scale: {
          mode: Phaser.Scale.FIT,
          autoCenter: Phaser.Scale.CENTER_BOTH,
        },
        scene: SnakeScene,
      });
    }).catch((error) => {
      if (disposed) return;
      runDescriptor.current = null;
      verifiedRun.current = null;
      setStarted(false);
      setStatus("ready");
      setPaused(false);
      setMode("local");
      toast({
        title: t("Snake engine unavailable"),
        description: t(getErrorMessage(error)),
        tone: "error",
      });
    });

    return () => {
      disposed = true;
      directionControl.current = () => {};
      pauseControl.current = () => {};
      game?.destroy(true);
      host.replaceChildren();
    };
  }, [runId, started, submitVerifiedResult, t, toast]);

  const beginRun = useCallback((descriptor: RunDescriptor) => {
    runDescriptor.current = descriptor;
    setScore(descriptor.initialState.score);
    setPaused(false);
    setStatus(descriptor.initialState.status);
    setStarted(true);
    setRunId((current) => current + 1);
  }, []);

  const startRun = useCallback(async () => {
    if (startingRef.current || session.isLoading) return;
    startingRef.current = true;
    setStarting(true);
    setVerification("idle");
    try {
      if (session.data) {
        const gameSession = await gameResultsApi.createSession("snake");
        const initialRandom = new SnakeXorShift32(gameSession.seed);
        const reconstructed = createSnakeState(
          gameSession.initialState.width,
          gameSession.initialState.height,
          () => initialRandom.next(),
        );
        if (!matchesSeededInitialState(gameSession.initialState, reconstructed)) {
          throw new Error("The server returned an inconsistent seeded Snake state.");
        }
        verifiedRun.current = {
          runToken: gameSession.sessionId,
          sessionId: gameSession.sessionId,
          actions: [],
          startedAt: Date.now(),
          submitted: false,
        };
        setMode("verified");
        beginRun({
          runToken: gameSession.sessionId,
          seed: gameSession.seed,
          initialState: cloneSnakeState(gameSession.initialState),
          verified: true,
        });
      } else {
        const seed = randomSeed();
        const random = new SnakeXorShift32(seed);
        verifiedRun.current = null;
        setMode("local");
        beginRun({
          runToken: crypto.randomUUID(),
          seed,
          initialState: createSnakeState(20, 15, () => random.next()),
          verified: false,
        });
      }
    } catch (error) {
      toast({
        title: t("Snake session unavailable"),
        description: t(getErrorMessage(error)),
        tone: "error",
      });
    } finally {
      startingRef.current = false;
      setStarting(false);
    }
  }, [beginRun, session.data, session.isLoading, t, toast]);

  function togglePause() {
    const next = !paused;
    setPaused(next);
    pauseControl.current(next);
  }

  const controls: Array<[SnakeDirection, string, typeof ArrowUp]> = [
    ["up", "Move up", ArrowUp],
    ["left", "Move left", ArrowLeft],
    ["down", "Move down", ArrowDown],
    ["right", "Move right", ArrowRight],
  ];

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,760px)_240px] xl:justify-center">
      <div>
        <div className="mb-3 grid grid-cols-4 gap-px border border-[var(--line)] bg-[var(--line)]">
          {[
            ["Score", score],
            ["Best", best],
            ["State", status === "playing" && paused ? "paused" : status],
            ["Channel", mode],
          ].map(([label, value]) => (
            <dl className="bg-[var(--surface)] p-3" key={label}>
              <dt className="font-telemetry text-[8px] text-[var(--muted)]">
                {t(String(label))}
              </dt>
              <dd className="mt-1 truncate font-mono text-sm font-bold uppercase">
                {typeof value === "number" ? formatNumber(value) : t(String(value))}
              </dd>
            </dl>
          ))}
        </div>
        <div className="relative aspect-[4/3] overflow-hidden border border-[var(--line-strong)] bg-[#0d0d0d]">
          <div ref={canvasHost} className="game-canvas h-full w-full" />
          {!started || status === "over" || status === "won" ? (
            <div className="absolute inset-5 grid place-items-center border border-[#4a4a4a] bg-[#111111] p-6 text-center text-[#eeeae3]">
              <div>
                <p className="font-telemetry text-[9px] text-[var(--accent)]">
                  {!started
                    ? t("[ PHASER ENGINE / READY ]")
                    : status === "won"
                      ? t("[ GRID CLEARED ]")
                      : t("[ COLLISION ]")}
                </p>
                <h3 className="mt-3 text-4xl font-black uppercase tracking-[-0.055em]">
                  {!started
                    ? "Snake"
                    : status === "won"
                      ? t("Perfect run")
                      : t("Run over")}
                </h3>
                <p className="mt-3 text-xs leading-5 text-[#97938d]">
                  {mode === "verified" && started
                    ? verification === "accepted"
                      ? t("The server replay matched and the result is recorded.")
                      : verification === "submitting"
                        ? t("The terminal replay is being verified by the server.")
                        : verification === "rejected"
                          ? t("The server rejected this replay; no rank was recorded.")
                          : t("Every effective direction is recorded once per simulation tick.")
                    : session.data
                      ? t("Start a seeded run. The server derives the final score from its replay.")
                      : t("Local practice is available; sign in for verified rankings and EXP.")}
                </p>
                <div className="mt-6 flex flex-wrap justify-center gap-3">
                  <Button
                    onClick={() => void startRun()}
                    busy={starting || session.isLoading}
                  >
                    {started ? <RotateCw size={14} aria-hidden="true" /> : null}
                    {t(started ? "Start another run" : "Start run")}
                  </Button>
                  <Link href="/games" className={buttonStyles("secondary")}>
                    {t("Back to games")}
                  </Link>
                </div>
              </div>
            </div>
          ) : null}
          {paused && status === "playing" ? (
            <div className="font-telemetry absolute inset-0 grid place-items-center bg-[#0d0d0d]/90 text-xs text-white">
              {t("[ RUN PAUSED ]")}
            </div>
          ) : null}
        </div>
      </div>

      <aside className="border border-[var(--line)] bg-[var(--surface)] p-4">
        <p className="font-telemetry text-[9px] text-[var(--muted)]">
          {t("[ INPUT ARRAY ]")}
        </p>
        <div className="mx-auto mt-6 grid w-40 grid-cols-3 gap-2">
          <span />
          {(() => {
            const [direction, label, Icon] = controls[0];
            return (
              <Button
                compact
                variant="secondary"
                aria-label={t(label)}
                disabled={status !== "playing" || paused}
                onClick={() => directionControl.current(direction)}
              >
                <Icon size={18} aria-hidden="true" />
              </Button>
            );
          })()}
          <span />
          {controls.slice(1).map(([direction, label, Icon]) => (
            <Button
              compact
              variant="secondary"
              aria-label={t(label)}
              key={direction}
              disabled={status !== "playing" || paused}
              onClick={() => directionControl.current(direction)}
            >
              <Icon size={18} aria-hidden="true" />
            </Button>
          ))}
        </div>
        <p className="mt-7 text-xs leading-5 text-[var(--muted)]">
          {t("Keyboard: arrow keys or WASD. Touch: use the direction array.")}
        </p>
        <div className="mt-6 border border-[var(--line)] p-3">
          <ShieldCheck size={15} className="text-[var(--accent)]" aria-hidden="true" />
          <p className="font-telemetry mt-3 text-[8px] text-[var(--muted)]">
            {mode === "verified"
              ? t("SERVER-SEED / REPLAY VERIFIED")
              : t("LOCAL PRACTICE / NO RANK")}
          </p>
        </div>
        {started && status === "playing" ? (
          <div className="mt-6 grid gap-2">
            <Button variant="secondary" compact onClick={togglePause}>
              {paused ? (
                <Play size={13} aria-hidden="true" />
              ) : (
                <Pause size={13} aria-hidden="true" />
              )}
              {t(paused ? "Resume" : "Pause")}
            </Button>
            <Button
              variant="ghost"
              compact
              onClick={() => void startRun()}
              busy={starting || session.isLoading}
            >
              <RotateCw size={13} aria-hidden="true" />
              {t("Restart")}
            </Button>
          </div>
        ) : null}
      </aside>
    </div>
  );
}
