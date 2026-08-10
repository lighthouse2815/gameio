const GAME_ARTWORK: Record<string, string> = {
  "2048": "/game-art/2048.webp",
  snake: "/game-art/snake.webp",
  "tic-tac-toe": "/game-art/tic-tac-toe.webp",
  caro: "/game-art/caro.webp",
  "tank-battle": "/game-art/tank-battle.webp",
};

export function getGameArtwork(
  slug: string,
  catalogArtwork?: string | null,
) {
  return GAME_ARTWORK[slug] ?? catalogArtwork ?? null;
}
