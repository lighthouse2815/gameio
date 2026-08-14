import type {
  GameSessionInitialStates,
  GameSessionResponse,
} from "@/features/games/game-results-api";
import type { GameResultSummary } from "@/features/games/types";
import {
  createInitialBoardSeeded,
  isGameOver,
  moveBoard,
  spawnTileSeeded,
  XorShift32,
  type MoveDirection,
} from "@/games/game2048/engine";
import {
  createSnakeState,
  queueDirection,
  SnakeXorShift32,
  stepSnake,
  type SnakeDirection,
} from "@/games/snake/engine";
import { FlappyEngine, type FlappyAction } from "@/games/flappy-bird/engine";
import { BreakoutEngine, type BreakoutDirection } from "@/games/breakout/engine";
import { MinesweeperEngine } from "@/games/minesweeper/engine";
import { MemoryMatchEngine } from "@/games/memory-match/engine";

type OfflineSlug = keyof GameSessionInitialStates;
type OfflineSession = { slug: OfflineSlug; seed: number; startedAt: number };

const sessions = new Map<string, OfflineSession>();

const NAMES: Record<OfflineSlug, string> = {
  "2048": "2048",
  snake: "Snake",
  "flappy-bird": "Flappy Bird",
  breakout: "Breakout",
  minesweeper: "Minesweeper",
  "memory-match": "Memory Match",
};

function randomSeed() {
  const values = new Uint32Array(1);
  crypto.getRandomValues(values);
  return values[0] || 1;
}

function initialState(slug: OfflineSlug, seed: number): GameSessionInitialStates[OfflineSlug] {
  if (slug === "2048") {
    const random = new XorShift32(seed);
    const board = createInitialBoardSeeded(random);
    return {
      board,
      score: 0,
      gameOver: false,
      highestValue: Math.max(...board.flat()),
    };
  }
  if (slug === "snake") {
    const random = new SnakeXorShift32(seed);
    return createSnakeState(20, 15, () => random.next());
  }
  if (slug === "flappy-bird") return new FlappyEngine(seed).state();
  if (slug === "breakout") return new BreakoutEngine(seed).state();
  if (slug === "minesweeper") return new MinesweeperEngine(seed).state();
  return new MemoryMatchEngine(seed).state();
}

export function isOfflinePlayMode() {
  return (
    typeof window !== "undefined" &&
    (window.location.pathname.startsWith("/offline") || navigator.onLine === false)
  );
}

export function createOfflineSession<Slug extends OfflineSlug>(slug: Slug): GameSessionResponse<Slug> {
  const seed = randomSeed();
  const sessionId = `offline-${crypto.randomUUID()}`;
  sessions.set(sessionId, { slug, seed, startedAt: Date.now() });
  return {
    sessionId,
    gameSlug: slug,
    seed,
    initialState: initialState(slug, seed) as GameSessionInitialStates[Slug],
    expiresAt: new Date(Date.now() + 86_400_000).toISOString(),
    challengeDate: null,
  };
}

function replay2048(seed: number, actions: string[]) {
  const random = new XorShift32(seed);
  let board = createInitialBoardSeeded(random);
  let score = 0;
  for (const action of actions) {
    const moved = moveBoard(board, action.toLowerCase() as MoveDirection);
    if (!moved.moved) continue;
    board = spawnTileSeeded(moved.board, random);
    score += moved.scoreDelta;
    if (isGameOver(board)) break;
  }
  return score;
}

function replaySnake(seed: number, actions: string[]) {
  const random = new SnakeXorShift32(seed);
  let state = createSnakeState(20, 15, () => random.next());
  for (const action of actions) {
    state = queueDirection(state, action.toLowerCase() as SnakeDirection);
    state = stepSnake(state, () => random.next());
    if (state.status !== "playing") break;
  }
  return state.score;
}

function replayScore(session: OfflineSession, actions: string[]) {
  if (session.slug === "2048") return replay2048(session.seed, actions);
  if (session.slug === "snake") return replaySnake(session.seed, actions);
  if (session.slug === "flappy-bird") {
    const engine = new FlappyEngine(session.seed);
    actions.some((action) => {
      engine.step(action as FlappyAction);
      return engine.terminal();
    });
    return engine.state().score;
  }
  if (session.slug === "breakout") {
    const engine = new BreakoutEngine(session.seed);
    actions.some((action) => {
      [...action].forEach((direction) => engine.step(direction as BreakoutDirection));
      return engine.terminal();
    });
    return engine.state().score;
  }
  if (session.slug === "minesweeper") {
    const engine = new MinesweeperEngine(session.seed);
    actions.some((action) => {
      engine.reveal(Number(action.slice(2)));
      return engine.terminal();
    });
    return engine.state().score;
  }
  const engine = new MemoryMatchEngine(session.seed);
  actions.some((action) => {
    engine.select(Number(action.slice(2)));
    return engine.terminal();
  });
  return engine.state().score;
}

export function completeOfflineSession(input: {
  sessionId: string;
  actions: string[];
  durationSeconds: number;
}): GameResultSummary {
  const session = sessions.get(input.sessionId);
  if (!session) throw new Error("Offline game session was not found.");
  sessions.delete(input.sessionId);
  const score = replayScore(session, input.actions);
  const bestKey = `gameio.offline-best.${session.slug}`;
  const storedBest = Number(window.localStorage.getItem(bestKey));
  const previousBestScore = Number.isFinite(storedBest) ? storedBest : null;
  const personalBest = previousBestScore == null || score > previousBestScore;
  if (personalBest) window.localStorage.setItem(bestKey, String(score));
  return {
    id: `offline-result-${crypto.randomUUID()}`,
    sessionId: input.sessionId,
    gameId: `offline-${session.slug}`,
    gameSlug: session.slug,
    gameName: NAMES[session.slug],
    username: "Offline player",
    score,
    result: "COMPLETED",
    durationSeconds: input.durationSeconds,
    playedAt: new Date().toISOString(),
    expAwarded: 0,
    resultingLevel: 0,
    previousBestScore,
    personalBest,
    unlockedAchievements: [],
    offline: true,
  };
}
