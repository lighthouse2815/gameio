import { apiClient } from "@/lib/api/client";
import type {
  AchievementSummary,
  GameDetail,
  GameFilters,
  GameListResponse,
  GameResultSummary,
} from "@/features/games/types";
import type { PageResponse } from "@/types/page";

export const gameApi = {
  list: (filters: GameFilters = {}) =>
    apiClient.get<GameListResponse>("/games", {
      auth: false,
      query: {
        search: filters.q,
        category: filters.category,
        gameType: filters.gameType,
        page: filters.page ?? 0,
        size: filters.size ?? 18,
      },
    }),
  bySlug: (slug: string) =>
    apiClient.get<GameDetail>("/games/" + encodeURIComponent(slug), {
      auth: false,
    }),
  achievements: () =>
    apiClient.get<AchievementSummary[]>("/achievements", { auth: false }),
  recent: (size = 4) =>
    apiClient.get<PageResponse<GameResultSummary> | GameResultSummary[]>(
      "/game-results/me",
      { query: { size } },
    ),
};
