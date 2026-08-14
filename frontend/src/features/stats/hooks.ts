"use client";

import { useQuery } from "@tanstack/react-query";
import { statsApi } from "@/features/stats/api";

export function usePlayerStats(enabled: boolean) {
  return useQuery({
    queryKey: ["player-stats"],
    queryFn: statsApi.me,
    enabled,
    staleTime: 30_000,
  });
}
