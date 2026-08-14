import type {
  DailyChallenge,
  DailyChallengeLeaderboard,
  DailyChallengeProgress,
} from "@/features/daily-challenge/types";
import type {
  GameSessionInitialStates,
  GameSessionResponse,
} from "@/features/games/game-results-api";
import { apiClient } from "@/lib/api/client";

export const dailyChallengeApi = {
  today: () =>
    apiClient.get<DailyChallenge>("/daily-challenges/today", {
      auth: false,
    }),
  progress: () =>
    apiClient.get<DailyChallengeProgress>("/daily-challenges/me"),
  leaderboard: (date: string, page = 0, size = 20) =>
    apiClient.get<DailyChallengeLeaderboard>(
      "/daily-challenges/" + encodeURIComponent(date) + "/leaderboard",
      { auth: false, query: { page, size } },
    ),
  startSession: <Slug extends keyof GameSessionInitialStates>() =>
    apiClient.post<GameSessionResponse<Slug>>(
      "/daily-challenges/today/sessions",
    ),
};
