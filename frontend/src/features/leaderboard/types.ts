import type { PageResponse } from "@/types/page";

export type LeaderboardEntry = {
  rank: number;
  userId?: string;
  username: string;
  avatarUrl?: string | null;
  level?: number;
  score: number;
  wins: number;
  gamesPlayed?: number;
};

export type LeaderboardResponse =
  | PageResponse<LeaderboardEntry>
  | LeaderboardEntry[];
