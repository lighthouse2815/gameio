import { describe, expect, it } from "vitest";
import { relatedAchievementCodes } from "@/features/games/related-achievements";

describe("relatedAchievementCodes", () => {
  it("includes only real shared and Snake-specific objectives", () => {
    expect([...relatedAchievementCodes("snake", false)]).toEqual([
      "FIRST_GAME",
      "PLAY_10_GAMES",
      "SCORE_1000_SNAKE",
    ]);
  });

  it("includes multiplayer and Tic Tac Toe objectives", () => {
    expect([...relatedAchievementCodes("tic-tac-toe", true)]).toEqual([
      "FIRST_GAME",
      "PLAY_10_GAMES",
      "FIRST_WIN",
      "WIN_10_GAMES",
      "WIN_5_TICTACTOE",
    ]);
  });
});
