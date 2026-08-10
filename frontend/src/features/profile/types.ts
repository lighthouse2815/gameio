import type { AchievementSummary } from "@/features/games/types";

export type PlayerProfile = {
  id: string;
  username: string;
  avatarUrl?: string | null;
  level: number;
  exp: number;
  gamesPlayed: number;
  wins: number;
  achievements: AchievementSummary[];
  createdAt: string;
};
