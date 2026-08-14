export type StatsSummary = {
  gamesPlayed: number;
  wins: number;
  losses: number;
  draws: number;
  completed: number;
  totalScore: number;
  bestScore: number;
  averageScore: number;
  totalDurationSeconds: number;
  winRate: number;
  activeDays: number;
  currentPlayStreak: number;
};

export type GameStats = {
  gameId: string;
  gameSlug: string;
  gameName: string;
  gamesPlayed: number;
  wins: number;
  losses: number;
  draws: number;
  completed: number;
  totalScore: number;
  bestScore: number;
  averageScore: number;
  totalDurationSeconds: number;
  winRate: number;
  lastPlayedAt: string;
};

export type DailyActivity = {
  date: string;
  gamesPlayed: number;
  wins: number;
  score: number;
  durationSeconds: number;
};

export type PlayerStats = {
  summary: StatsSummary;
  games: GameStats[];
  activity: DailyActivity[];
  scoreTrend: {
    recentSevenDayAverage: number;
    previousSevenDayAverage: number;
    percentChange?: number | null;
  };
  achievements: {
    unlocked: number;
    total: number;
    completionPercent: number;
  };
  mostPlayedGameSlug?: string | null;
  strongestMultiplayerGameSlug?: string | null;
};
