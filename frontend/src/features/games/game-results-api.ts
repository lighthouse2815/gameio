import type {
  GameResultSummary,
} from "@/features/games/types";
import type { Board, MoveDirection } from "@/games/game2048/engine";
import type {
  SnakeDirection,
  SnakeState,
} from "@/games/snake/engine";
import type {
  FlappyAction,
  FlappyState,
} from "@/games/flappy-bird/engine";
import type {
  BreakoutDirection,
  BreakoutState,
} from "@/games/breakout/engine";
import type {
  MinesweeperReplayAction,
  MinesweeperState,
} from "@/games/minesweeper/engine";
import type {
  MemoryReplayAction,
  MemoryState,
} from "@/games/memory-match/engine";
import { apiClient } from "@/lib/api/client";

export type Game2048InitialState = {
  board: Board;
  score: number;
  gameOver: boolean;
  highestValue: number;
};

export type GameSessionInitialStates = {
  "2048": Game2048InitialState;
  snake: SnakeState;
  "flappy-bird": FlappyState;
  breakout: BreakoutState;
  minesweeper: MinesweeperState;
  "memory-match": MemoryState;
};

export type GameSessionResponse<
  Slug extends keyof GameSessionInitialStates,
> = {
  sessionId: string;
  gameSlug: Slug;
  seed: number;
  initialState: GameSessionInitialStates[Slug];
  expiresAt: string;
};

export type VerifiedAction = Uppercase<MoveDirection>;
export type VerifiedSnakeAction = Uppercase<SnakeDirection>;
export type BreakoutReplayAction =
  | BreakoutDirection
  | `${BreakoutDirection}${BreakoutDirection}`
  | `${BreakoutDirection}${BreakoutDirection}${BreakoutDirection}`;
export type VerifiedReplayAction =
  | VerifiedAction
  | VerifiedSnakeAction
  | FlappyAction
  | BreakoutReplayAction
  | MinesweeperReplayAction
  | MemoryReplayAction;

export const gameResultsApi = {
  createSession: <Slug extends keyof GameSessionInitialStates>(
    gameSlug: Slug,
  ) =>
    apiClient.post<GameSessionResponse<Slug>>("/game-results/sessions", {
      gameSlug,
    }),
  complete: (input: {
    sessionId: string;
    actions: string[];
    durationSeconds: number;
  }) => apiClient.post<GameResultSummary>("/game-results", input),
};
