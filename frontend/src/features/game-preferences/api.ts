import { apiClient } from "@/lib/api/client";
import type { GamePreference } from "@/features/game-preferences/types";

export const gamePreferencesApi = {
  list: () => apiClient.get<GamePreference[]>("/game-preferences/me"),
  favorite: (gameId: string, favorite: boolean) =>
    apiClient.put<GamePreference>(
      `/game-preferences/${encodeURIComponent(gameId)}/favorite`,
      { favorite },
    ),
  played: (gameId: string) =>
    apiClient.post<GamePreference>(
      `/game-preferences/${encodeURIComponent(gameId)}/played`,
    ),
};
