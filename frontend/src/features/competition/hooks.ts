"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { competitionApi } from "@/features/competition/api";

export const competitionKeys = {
  all: ["competition"] as const,
  season: () => [...competitionKeys.all, "season"] as const,
  ratings: (gameId: string) => [...competitionKeys.all, "ratings", gameId] as const,
  mine: () => [...competitionKeys.all, "ratings", "me"] as const,
  tournaments: () => [...competitionKeys.all, "tournaments"] as const,
  tournament: (id: string) => [...competitionKeys.tournaments(), id] as const,
};

export function useCurrentSeason() {
  return useQuery({
    queryKey: competitionKeys.season(),
    queryFn: competitionApi.season,
    staleTime: 5 * 60_000,
  });
}

export function useCompetitionRatings(gameId: string) {
  return useQuery({
    queryKey: competitionKeys.ratings(gameId),
    queryFn: () => competitionApi.ratings(gameId),
    enabled: Boolean(gameId),
    placeholderData: keepPreviousData,
  });
}

export function useMyRatings(enabled: boolean) {
  return useQuery({
    queryKey: competitionKeys.mine(),
    queryFn: competitionApi.myRatings,
    enabled,
  });
}

export function useTournaments() {
  return useQuery({
    queryKey: competitionKeys.tournaments(),
    queryFn: () => competitionApi.tournaments(),
    refetchInterval: 10_000,
  });
}

export function useTournament(id: string) {
  return useQuery({
    queryKey: competitionKeys.tournament(id),
    queryFn: () => competitionApi.tournament(id),
    enabled: Boolean(id),
    refetchInterval: (query) =>
      query.state.data?.tournament.status === "IN_PROGRESS" ? 5_000 : false,
  });
}

