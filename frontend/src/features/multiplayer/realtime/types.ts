import type { AchievementSummary } from "@/features/games/types";
import type { GameRoom, RoomPlayer } from "@/features/multiplayer/types";
import type {
  ServerEnvelope,
  SocketStatus,
} from "@/lib/socket/game-socket-client";

export type BoardMarker = "" | "X" | "O";

export type TicTacToeSnapshot = {
  sequence: number;
  board: BoardMarker[][];
  currentTurnPlayerId: string | null;
  winnerId: string | null;
  draw: boolean;
};

export type CaroSnapshot = TicTacToeSnapshot & {
  boardSize: number;
};

export type TankView = {
  userId: string;
  x: number;
  y: number;
  rotation: number;
  hp: number;
  alive: boolean;
  kills: number;
  lastInputSequence: number;
};

export type BulletView = {
  id: string;
  ownerId: string;
  x: number;
  y: number;
};

export type TankSnapshot = {
  sequence: number;
  width: number;
  height: number;
  tanks: TankView[];
  bullets: BulletView[];
  winnerId: string | null;
  draw: boolean;
};

export type GameSnapshot =
  | TicTacToeSnapshot
  | CaroSnapshot
  | TankSnapshot;

export type GameStartPayload = {
  matchId: string;
  gameId: string;
  gameSlug: string;
  players: RoomPlayer[];
  startedAt: string;
  state: GameSnapshot;
};

export type PlayerProgression = {
  userId: string;
  result: "WIN" | "LOSS" | "DRAW" | "COMPLETED";
  score: number;
  expAwarded: number;
  level: number;
  unlockedAchievements: AchievementSummary[];
};

export type GameOverPayload = {
  matchId: string;
  finalState: GameSnapshot;
  progression: PlayerProgression[];
};

export type RealtimeError = {
  code: string;
  message: string;
  requestId?: string | null;
};

export type OpponentDisconnected = {
  userId: string;
  reconnectGraceSeconds: number;
};

export type RealtimeGameState = {
  connection: SocketStatus;
  room: GameRoom | null;
  gameSlug: string | null;
  matchId: string | null;
  snapshot: GameSnapshot | null;
  gameOver: GameOverPayload | null;
  error: RealtimeError | null;
  opponentDisconnected: OpponentDisconnected | null;
  pendingRequestIds: string[];
  lastRequestId: string | null;
};

export type RealtimeAction =
  | { type: "socket_status"; status: SocketStatus }
  | { type: "server_event"; event: ServerEnvelope }
  | { type: "request_sent"; requestId: string }
  | { type: "clear_error" };
