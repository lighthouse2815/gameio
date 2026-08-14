import type { LeaderboardResponse } from "@/features/leaderboard/types";

export type DailyChallenge = {
  date: string;
  gameId: string;
  gameSlug: string;
  gameName: string;
  gameDescription: string;
  startsAt: string;
  endsAt: string;
};

export type DailyChallengeProgress = {
  date: string;
  completedToday: boolean;
  todayBestScore: number;
  completedDays: number;
  currentStreak: number;
  longestStreak: number;
  distinctSoloGames: number;
};

export type DailyChallengeLeaderboard = LeaderboardResponse;
