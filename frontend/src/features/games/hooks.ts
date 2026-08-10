"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { gameApi } from "@/features/games/api";
import type { GameFilters } from "@/features/games/types";
import { asPage } from "@/types/page";

export const gameKeys = {
  all: ["games"] as const,
  lists: () => [...gameKeys.all, "list"] as const,
  list: (filters: GameFilters) => [...gameKeys.lists(), filters] as const,
  detail: (slug: string) => [...gameKeys.all, "detail", slug] as const,
  achievements: () => [...gameKeys.all, "achievements"] as const,
  recent: (size: number) => [...gameKeys.all, "recent", size] as const,
};

export function useGames(filters: GameFilters = {}) {
  return useQuery({
    queryKey: gameKeys.list(filters),
    queryFn: () => gameApi.list(filters),
    select: (response) =>
      asPage(response, filters.page ?? 0, filters.size ?? 18),
    placeholderData: keepPreviousData,
  });
}

export function useGame(slug: string) {
  return useQuery({
    queryKey: gameKeys.detail(slug),
    queryFn: () => gameApi.bySlug(slug),
    enabled: Boolean(slug),
  });
}

export function useAchievements() {
  return useQuery({
    queryKey: gameKeys.achievements(),
    queryFn: gameApi.achievements,
    staleTime: 5 * 60_000,
  });
}

export function useRecentGames(enabled: boolean, size = 4) {
  return useQuery({
    queryKey: gameKeys.recent(size),
    queryFn: () => gameApi.recent(size),
    select: (response) => asPage(response, 0, size),
    enabled,
  });
}
