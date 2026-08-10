import type { LeaderboardResponse } from "@/features/leaderboard/types";
import { apiClient } from "@/lib/api/client";

export const leaderboardApi = {
  global: (page = 0, size = 25) =>
    apiClient.get<LeaderboardResponse>("/leaderboards", {
      auth: false,
      query: { page, size },
    }),
  forGame: (gameId: string, page = 0, size = 25) =>
    apiClient.get<LeaderboardResponse>(
      "/games/" + encodeURIComponent(gameId) + "/leaderboard",
      {
        auth: false,
        query: { page, size },
      },
    ),
};
