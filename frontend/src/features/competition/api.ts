import type {
  RatingEntry,
  RatingPage,
  Season,
  TournamentDetail,
  TournamentPage,
} from "@/features/competition/types";
import { apiClient } from "@/lib/api/client";

export const competitionApi = {
  season: () =>
    apiClient.get<Season>("/competition/season", { auth: false }),
  ratings: (gameId: string, page = 0, size = 20) =>
    apiClient.get<RatingPage>("/competition/ratings", {
      auth: false,
      query: { gameId, page, size },
    }),
  myRatings: () =>
    apiClient.get<RatingEntry[]>("/competition/ratings/me"),
  tournaments: (page = 0, size = 30) =>
    apiClient.get<TournamentPage>("/competition/tournaments", {
      auth: false,
      query: { page, size },
    }),
  tournament: (tournamentId: string) =>
    apiClient.get<TournamentDetail>(
      `/competition/tournaments/${encodeURIComponent(tournamentId)}`,
      { auth: false },
    ),
  createTournament: (input: {
    name: string;
    gameId: string;
    maxPlayers: "4" | "8" | "16";
  }) => apiClient.post<TournamentDetail>("/competition/tournaments", input),
  joinTournament: (tournamentId: string) =>
    apiClient.post<TournamentDetail>(
      `/competition/tournaments/${encodeURIComponent(tournamentId)}/join`,
    ),
  startTournament: (tournamentId: string) =>
    apiClient.post<TournamentDetail>(
      `/competition/tournaments/${encodeURIComponent(tournamentId)}/start`,
    ),
};

