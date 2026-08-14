import type { PageResponse } from "@/types/page";

export type GameType =
  | "SINGLE_PLAYER"
  | "TURN_BASED_MULTIPLAYER"
  | "REALTIME_MULTIPLAYER";

export type GameCategory =
  | "CASUAL"
  | "PUZZLE"
  | "ACTION"
  | "ARCADE"
  | string;

export type GameSummary = {
  id: string;
  name: string;
  slug: string;
  description: string;
  thumbnailUrl?: string | null;
  category: GameCategory;
  gameType: GameType;
  minPlayers: number;
  maxPlayers: number;
  onlinePlayers: number;
  playsCount: number;
  createdAt: string;
};

export type AchievementSummary = {
  id: string;
  code: string;
  name: string;
  description: string;
  icon?: string | null;
  expReward: number;
  unlocked?: boolean;
};

export type GameDetail = GameSummary;

export type GameFilters = {
  q?: string;
  category?: string;
  gameType?: GameType | "";
  page?: number;
  size?: number;
};

export type GameListResponse = PageResponse<GameSummary> | GameSummary[];

export type GameResultSummary = {
  id: string;
  sessionId: string;
  gameId: string;
  gameSlug: string;
  gameName: string;
  username: string;
  score: number;
  result: "WIN" | "LOSS" | "DRAW" | "COMPLETED";
  durationSeconds: number;
  playedAt: string;
  expAwarded: number;
  resultingLevel: number;
  previousBestScore?: number | null;
  personalBest: boolean;
  unlockedAchievements: AchievementSummary[];
  offline?: boolean;
};
