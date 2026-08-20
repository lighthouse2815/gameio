const GAME_ARTWORK: Record<string, string> = {
  "2048": "/game-art/2048.webp",
  snake: "/game-art/snake.webp",
  "flappy-bird": "/game-art/flappy-bird.webp",
  breakout: "/game-art/breakout.svg",
  minesweeper: "/game-art/minesweeper.svg",
  "memory-match": "/game-art/memory-match.svg",
  "typing-race": "/game-art/typing-race.svg",
  "tic-tac-toe": "/game-art/tic-tac-toe.webp",
  caro: "/game-art/caro.webp",
  "tank-battle": "/game-art/tank-battle.webp",
  "connect-four": "/game-art/connect-four.svg",
  reversi: "/game-art/reversi.svg",
  "rock-paper-scissors": "/game-art/rock-paper-scissors.svg",
};

export function getGameArtwork(
  slug: string,
  catalogArtwork?: string | null,
) {
  return GAME_ARTWORK[slug] ?? catalogArtwork ?? null;
}
