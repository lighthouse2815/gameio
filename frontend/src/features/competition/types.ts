import type { PageResponse } from "@/types/page";

export type Season = {
  id: string;
  code: string;
  name: string;
  startsAt: string;
  endsAt: string;
};

export type RatingEntry = {
  rank: number;
  userId: string;
  username: string;
  avatarUrl?: string | null;
  gameId: string;
  gameSlug: string;
  rating: number;
  gamesPlayed: number;
  wins: number;
  losses: number;
  draws: number;
  updatedAt: string;
};

export type TournamentStatus =
  | "REGISTRATION"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export type TournamentSummary = {
  id: string;
  name: string;
  gameId: string;
  gameSlug: string;
  gameName: string;
  createdById: string;
  createdByUsername: string;
  status: TournamentStatus;
  maxPlayers: number;
  joinedPlayers: number;
  currentRound: number;
  winnerId?: string | null;
  winnerUsername?: string | null;
  createdAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
};

export type TournamentPlayer = {
  userId: string;
  username: string;
  avatarUrl?: string | null;
  seedNumber: number;
  eliminated: boolean;
  joinedAt: string;
};

export type TournamentMatch = {
  id: string;
  roundNumber: number;
  bracketIndex: number;
  playerOneId: string;
  playerOneUsername: string;
  playerTwoId?: string | null;
  playerTwoUsername?: string | null;
  winnerId?: string | null;
  winnerUsername?: string | null;
  roomId?: string | null;
  status: "ACTIVE" | "COMPLETED";
  completedAt?: string | null;
};

export type TournamentDetail = {
  tournament: TournamentSummary;
  players: TournamentPlayer[];
  matches: TournamentMatch[];
};

export type RatingPage = PageResponse<RatingEntry>;
export type TournamentPage = PageResponse<TournamentSummary>;

