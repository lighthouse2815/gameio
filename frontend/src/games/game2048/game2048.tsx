"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  RotateCw,
  ShieldCheck,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useSession } from "@/features/auth/hooks";
import {
  gameResultsApi,
  type VerifiedAction,
} from "@/features/games/game-results-api";
import { getErrorMessage } from "@/lib/api/api-error";
import { getTileAppearance } from "@/games/game2048/tile-appearance";
import { useI18n } from "@/lib/i18n/use-i18n";
import {
  createInitialBoardSeeded,
  createEmptyBoard,
  createInitialBoard,
  hasWon,
  isGameOver,
  moveBoard,
  spawnTile,
  spawnTileSeeded,
  XorShift32,
  type Board,
  type MoveDirection,
} from "@/games/game2048/engine";

const BEST_SCORE_KEY = "gameio.2048.best";

export default function Game2048() {
  const { t, formatNumber } = useI18n();
  const toast = useToast();
  const session = useSession();
  const queryClient = useQueryClient();
  const [board, setBoard] = useState<Board>(() => createEmptyBoard());
  const [score, setScore] = useState(0);
  const [best, setBest] = useState(() => {
    if (typeof window === "undefined") return 0;
    const stored = Number(window.localStorage.getItem(BEST_SCORE_KEY));
    return Number.isFinite(stored) && stored > 0 ? stored : 0;
  });
  const [status, setStatus] = useState<"ready" | "playing" | "over">("ready");
  const [won, setWon] = useState(false);
  const [mode, setMode] = useState<"local" | "verified">("local");
  const [starting, setStarting] = useState(false);
  const [verification, setVerification] = useState<
    "idle" | "submitting" | "accepted" | "rejected"
  >("idle");
  const touchStart = useRef<{ x: number; y: number } | null>(null);
  const seededRandom = useRef<XorShift32 | null>(null);
  const verifiedRun = useRef<{
    sessionId: string;
    actions: VerifiedAction[];
    startedAt: number;
  } | null>(null);

  const startLocal = useCallback(() => {
    verifiedRun.current = null;
    seededRandom.current = null;
    setBoard(createInitialBoard());
    setScore(0);
    setWon(false);
    setMode("local");
    setVerification("idle");
    setStatus("playing");
  }, []);

  const start = useCallback(async () => {
    if (session.isLoading) return;
    if (!session.data) {
      startLocal();
      return;
    }
    setStarting(true);
    try {
      const gameSession = await gameResultsApi.createSession("2048");
      const random = new XorShift32(gameSession.seed);
      createInitialBoardSeeded(random);
      seededRandom.current = random;
      verifiedRun.current = {
        sessionId: gameSession.sessionId,
        actions: [],
        startedAt: Date.now(),
      };
      setBoard(gameSession.initialState.board);
      setScore(gameSession.initialState.score);
      setWon(gameSession.initialState.highestValue >= 2048);
      setMode("verified");
      setVerification("idle");
      setStatus(gameSession.initialState.gameOver ? "over" : "playing");
    } catch (error) {
      toast({
        title: t("Verified session unavailable"),
        description: t(getErrorMessage(error)),
        tone: "error",
      });
    } finally {
      setStarting(false);
    }
  }, [session.data, session.isLoading, startLocal, t, toast]);

  const submitVerifiedResult = useCallback(
    async (actions: VerifiedAction[]) => {
      const run = verifiedRun.current;
      if (!run) return;
      setVerification("submitting");
      try {
        const result = await gameResultsApi.complete({
          sessionId: run.sessionId,
          actions,
          durationSeconds: Math.max(
            1,
            Math.floor((Date.now() - run.startedAt) / 1000),
          ),
        });
        setVerification("accepted");
        toast({
          title: t("Result verified"),
          description:
            "+" + formatNumber(result.expAwarded) + " EXP / " + t("level") + " " + result.resultingLevel,
          tone: "success",
        });
        void queryClient.invalidateQueries({ queryKey: ["leaderboard"] });
        void queryClient.invalidateQueries({ queryKey: ["games", "recent"] });
        void queryClient.invalidateQueries({ queryKey: ["profile"] });
      } catch (error) {
        setVerification("rejected");
        toast({
          title: t("Result verification failed"),
          description: t(getErrorMessage(error)),
          tone: "error",
        });
      }
    },
    [formatNumber, queryClient, t, toast],
  );

  const move = useCallback(
    (direction: MoveDirection) => {
      if (status !== "playing") return;
      const result = moveBoard(board, direction);
      if (!result.moved) return;
      const nextBoard =
        mode === "verified" && seededRandom.current
          ? spawnTileSeeded(result.board, seededRandom.current)
          : spawnTile(result.board);
      const nextScore = score + result.scoreDelta;
      let recordedActions: VerifiedAction[] = [];
      if (mode === "verified" && verifiedRun.current) {
        const action = direction.toUpperCase() as VerifiedAction;
        recordedActions = [...verifiedRun.current.actions, action];
        verifiedRun.current.actions = recordedActions;
      }
      setBoard(nextBoard);
      setScore(nextScore);
      if (nextScore > best) {
        setBest(nextScore);
        window.localStorage.setItem(BEST_SCORE_KEY, String(nextScore));
      }
      if (!won && hasWon(nextBoard)) {
        setWon(true);
        toast({
          title: t("2048 reached"),
          description: t("The run continues. Push the grid further."),
          tone: "success",
        });
      }
      if (isGameOver(nextBoard)) {
        setStatus("over");
        if (mode === "verified") {
          void submitVerifiedResult(recordedActions);
        }
      }
    },
    [best, board, mode, score, status, submitVerifiedResult, t, toast, won],
  );

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const directions: Partial<Record<string, MoveDirection>> = {
        ArrowUp: "up",
        w: "up",
        ArrowDown: "down",
        s: "down",
        ArrowLeft: "left",
        a: "left",
        ArrowRight: "right",
        d: "right",
      };
      const direction = directions[event.key];
      if (direction) {
        event.preventDefault();
        move(direction);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [move]);

  return (
    <div className="grid gap-5 xl:grid-cols-[minmax(0,620px)_240px] xl:justify-center">
      <div>
        <div className="mb-3 grid grid-cols-4 gap-px bg-[var(--line)] border border-[var(--line)]">
          {[
            ["Score", score],
            ["Best", best],
            ["State", status],
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
        <div
          className="relative aspect-square border border-[var(--line-strong)] bg-[var(--line)] p-2 sm:p-3"
          onTouchStart={(event) => {
            const touch = event.changedTouches[0];
            touchStart.current = { x: touch.clientX, y: touch.clientY };
          }}
          onTouchEnd={(event) => {
            if (!touchStart.current) return;
            const touch = event.changedTouches[0];
            const deltaX = touch.clientX - touchStart.current.x;
            const deltaY = touch.clientY - touchStart.current.y;
            touchStart.current = null;
            if (Math.max(Math.abs(deltaX), Math.abs(deltaY)) < 24) return;
            if (Math.abs(deltaX) > Math.abs(deltaY)) {
              move(deltaX > 0 ? "right" : "left");
            } else {
              move(deltaY > 0 ? "down" : "up");
            }
          }}
          aria-label={t("2048 game board")}
        >
          <div className="grid h-full grid-cols-4 gap-2 sm:gap-3">
            {board.flatMap((row, rowIndex) =>
              row.map((value, columnIndex) => (
                <div
                  key={rowIndex + "-" + columnIndex}
                  className="grid min-w-0 place-items-center font-mono text-lg font-black tracking-[-0.06em] sm:text-3xl"
                  style={getTileAppearance(value)}
                >
                  {value || "0"}
                </div>
              )),
            )}
          </div>
          {status !== "playing" ? (
            <div className="absolute inset-5 grid place-items-center border border-[var(--line-strong)] bg-[var(--surface)]/95 p-6 text-center">
              <div>
                <p className="font-telemetry text-[9px] text-[var(--accent)]">
                  {status === "over"
                    ? verification === "accepted"
                      ? t("[ RESULT VERIFIED ]")
                      : verification === "submitting"
                        ? t("[ VERIFYING RESULT ]")
                        : t("[ GRID LOCKED ]")
                    : session.data
                      ? t("[ VERIFIED ENGINE READY ]")
                      : t("[ LOCAL ENGINE ]")}
                </p>
                <h3 className="mt-3 text-4xl font-black uppercase tracking-[-0.055em]">
                  {status === "over" ? t("Run over") : "2048"}
                </h3>
                <p className="mt-3 text-xs leading-5 text-[var(--muted)]">
                  {status === "over"
                    ? t("No legal movement remains.")
                    : t("Merge matching units. Reach 2048.")}
                </p>
                <Button className="mt-6" onClick={() => void start()} busy={session.isLoading || starting || verification === "submitting"}>
                  {status === "over" ? (
                    <RotateCw size={14} aria-hidden="true" />
                  ) : null}
                  {status === "over"
                    ? t("Restart run")
                    : session.data
                      ? t("Start verified run")
                      : t("Start local run")}
                </Button>
                {session.data && status === "ready" ? (
                  <Button className="ml-2 mt-6" variant="ghost" onClick={startLocal}>
                    {t("Local only")}
                  </Button>
                ) : null}
              </div>
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
          <Button compact variant="secondary" aria-label={t("Move up")} onClick={() => move("up")}>
            <ArrowUp size={18} aria-hidden="true" />
          </Button>
          <span />
          <Button compact variant="secondary" aria-label={t("Move left")} onClick={() => move("left")}>
            <ArrowLeft size={18} aria-hidden="true" />
          </Button>
          <Button compact variant="secondary" aria-label={t("Move down")} onClick={() => move("down")}>
            <ArrowDown size={18} aria-hidden="true" />
          </Button>
          <Button compact variant="secondary" aria-label={t("Move right")} onClick={() => move("right")}>
            <ArrowRight size={18} aria-hidden="true" />
          </Button>
        </div>
        <p className="mt-7 text-xs leading-5 text-[var(--muted)]">
          {t("Keyboard: arrow keys or WASD. Touch: swipe the grid or use the direction array.")}
        </p>
        {status === "playing" ? (
          <Button className="mt-6 w-full" variant="ghost" compact onClick={() => void start()} busy={session.isLoading || starting}>
            <RotateCw size={13} aria-hidden="true" />
            {t("Restart")}
          </Button>
        ) : null}
        <div className="font-telemetry mt-6 border-t border-[var(--line)] pt-4 text-[8px] leading-5 text-[var(--muted)]">
          {mode === "verified" ? (
            <span className="flex items-start gap-2 text-[var(--foreground)]">
              <ShieldCheck size={13} className="mt-0.5 text-[var(--accent)]" aria-hidden="true" />
              {t("SEEDED ACTION REPLAY / SERVER VERIFIES FINAL RESULT")}
            </span>
          ) : (
            t("LOCAL RUN / BEST SCORE REMAINS IN THIS BROWSER")
          )}
        </div>
      </aside>
    </div>
  );
}
