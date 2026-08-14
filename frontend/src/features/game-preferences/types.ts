export type GamePreference = {
  gameId: string;
  gameSlug: string;
  favorite: boolean;
  lastPlayedAt?: string | null;
  updatedAt: string;
};
