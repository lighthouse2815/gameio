const COMMON_CODES = ["FIRST_GAME", "PLAY_10_GAMES"] as const;
const MULTIPLAYER_CODES = ["FIRST_WIN", "WIN_10_GAMES"] as const;

const GAME_SPECIFIC_CODES: Readonly<Record<string, readonly string[]>> = {
  snake: ["SCORE_1000_SNAKE"],
  "tic-tac-toe": ["WIN_5_TICTACTOE"],
};

export function relatedAchievementCodes(
  gameSlug: string,
  multiplayer: boolean,
) {
  return new Set([
    ...COMMON_CODES,
    ...(multiplayer ? MULTIPLAYER_CODES : []),
    ...(GAME_SPECIFIC_CODES[gameSlug] ?? []),
  ]);
}
