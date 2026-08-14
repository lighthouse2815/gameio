import { apiClient } from "@/lib/api/client";
import type { PlayerStats } from "@/features/stats/types";

export const statsApi = {
  me: () => apiClient.get<PlayerStats>("/stats/me"),
};
