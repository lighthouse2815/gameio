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

export type ConnectFourMarker = "" | "R" | "Y";

export type ConnectFourSnapshot = {
  sequence: number;
  board: ConnectFourMarker[][];
  currentTurnPlayerId: string | null;
  winnerId: string | null;
  draw: boolean;
  lastMoveRow?: number | null;
  lastMoveColumn?: number | null;
};

export type ReversiMarker = "" | "B" | "W";

export type ReversiMove = {
  row: number;
  column: number;
};

export type ReversiSnapshot = {
  sequence: number;
  board: ReversiMarker[][];
  currentTurnPlayerId: string | null;
  winnerId: string | null;
  draw: boolean;
  blackCount: number;
  whiteCount: number;
  legalMoves: ReversiMove[];
  lastMoveRow?: number | null;
  lastMoveColumn?: number | null;
};

export type GridMove = {
  row: number;
  column: number;
};

export type UltimateSubBoardMarker = BoardMarker | "D";

export type UltimateTicTacToeSnapshot = {
  sequence: number;
  board: BoardMarker[][];
  subBoards: UltimateSubBoardMarker[][];
  forcedBoardRow?: number | null;
  forcedBoardColumn?: number | null;
  legalMoves: GridMove[];
  currentTurnPlayerId: string | null;
  winnerId: string | null;
  draw: boolean;
  lastMoveRow?: number | null;
  lastMoveColumn?: number | null;
};

export type EdgeOrientation = "H" | "V";

export type EdgeMove = {
  orientation: EdgeOrientation;
  row: number;
  column: number;
};

export type DotsAndBoxesSnapshot = {
  sequence: number;
  horizontalEdges: boolean[][];
  verticalEdges: boolean[][];
  boxes: ("" | "R" | "B")[][];
  scores: [number, number];
  legalMoves: EdgeMove[];
  lastEdge?: EdgeMove | null;
  currentTurnPlayerId: string | null;
  winnerId: string | null;
  draw: boolean;
};

export type MancalaSnapshot = {
  sequence: number;
  pits: number[];
  currentTurnPlayerId: string | null;
  winnerId: string | null;
  draw: boolean;
  legalPits: number[];
  lastPit?: number | null;
  scores: [number, number];
};

export type HexMarker = "" | "R" | "B";

export type HexSnapshot = {
  sequence: number;
  board: HexMarker[][];
  currentTurnPlayerId: string | null;
  winnerId: string | null;
  lastMoveRow?: number | null;
  lastMoveColumn?: number | null;
};

export type SosPlayerSnapshot = {
  userId: string;
  score: number;
};

export type SosSnapshot = {
  sequence: number;
  board: ("" | "S" | "O")[][];
  currentTurnPlayerId: string | null;
  players: [SosPlayerSnapshot, SosPlayerSnapshot];
  winnerId: string | null;
  draw: boolean;
  lastMoveRow?: number | null;
  lastMoveColumn?: number | null;
  lastMovePoints: number;
};

export type RpsChoice = "ROCK" | "PAPER" | "SCISSORS";

export type RpsPlayerSnapshot = {
  userId: string;
  wins: number;
  submitted: boolean;
};

export type RpsRoundSnapshot = {
  round: number;
  firstChoice: RpsChoice;
  secondChoice: RpsChoice;
  winnerId?: string | null;
  draw: boolean;
};

export type RpsSnapshot = {
  sequence: number;
  round: number;
  targetWins: number;
  players: RpsPlayerSnapshot[];
  lastRound?: RpsRoundSnapshot | null;
  winnerId?: string | null;
  draw: boolean;
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

export type TypingRacePlayerSnapshot = {
  userId: string;
  progress: number;
  correctCharacters: number;
  errors: number;
  combo: number;
  bestCombo: number;
  lastInputSequence: number;
  wpm: number;
  accuracyPercent: number;
  finished: boolean;
  finishedAt?: string | null;
};

export type TypingRaceSnapshot = {
  sequence: number;
  passageId: string;
  passage: string;
  startsAt: string;
  deadline: string;
  players: TypingRacePlayerSnapshot[];
  winnerId?: string | null;
  draw: boolean;
  terminal: boolean;
};

export type GameSnapshot =
  | TicTacToeSnapshot
  | CaroSnapshot
  | ConnectFourSnapshot
  | ReversiSnapshot
  | UltimateTicTacToeSnapshot
  | DotsAndBoxesSnapshot
  | MancalaSnapshot
  | HexSnapshot
  | SosSnapshot
  | RpsSnapshot
  | TankSnapshot
  | TypingRaceSnapshot;

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
  ratingBefore: number;
  ratingAfter: number;
  ratingDelta: number;
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
