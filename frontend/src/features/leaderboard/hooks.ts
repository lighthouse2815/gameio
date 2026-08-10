"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { leaderboardApi } from "@/features/leaderboard/api";
import { asPage } from "@/types/page";

export function useGlobalLeaderboard(page = 0, size = 25) {
  return useQuery({
    queryKey: ["leaderboard", "global", page, size],
    queryFn: () => leaderboardApi.global(page, size),
    select: (response) => asPage(response, page, size),
    placeholderData: keepPreviousData,
  });
}

export function useGameLeaderboard(
  gameId: string | undefined,
  page = 0,
  size = 25,
) {
  return useQuery({
    queryKey: ["leaderboard", "game", gameId, page, size],
    queryFn: () => leaderboardApi.forGame(gameId ?? "", page, size),
    select: (response) => asPage(response, page, size),
    enabled: Boolean(gameId),
    placeholderData: keepPreviousData,
  });
}
