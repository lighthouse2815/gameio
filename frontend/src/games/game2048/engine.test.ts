import { describe, expect, it } from "vitest";
import {
  isGameOver,
  moveBoard,
  spawnTile,
  createInitialBoardSeeded,
  XorShift32,
  type Board,
} from "@/games/game2048/engine";

describe("2048 engine", () => {
  it("merges each pair once and reports the score", () => {
    const board: Board = [
      [2, 2, 2, 2],
      [4, 4, 8, 0],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
    ];
    const result = moveBoard(board, "left");
    expect(result.board[0]).toEqual([4, 4, 0, 0]);
    expect(result.board[1]).toEqual([8, 8, 0, 0]);
    expect(result.scoreDelta).toBe(16);
    expect(result.moved).toBe(true);
  });

  it("moves columns in the requested direction", () => {
    const board: Board = [
      [2, 0, 0, 0],
      [2, 0, 0, 0],
      [4, 0, 0, 0],
      [4, 0, 0, 0],
    ];
    expect(moveBoard(board, "down").board.map((row) => row[0])).toEqual([
      0, 0, 4, 8,
    ]);
  });

  it("spawns deterministically with an injected random source", () => {
    const values = [0.5, 0.95];
    const next = spawnTile(
      [
        [0, 0],
        [0, 0],
      ],
      () => values.shift() ?? 0,
    );
    expect(next.flat().filter(Boolean)).toEqual([4]);
  });

  it("detects a locked board but allows adjacent equal tiles", () => {
    expect(
      isGameOver([
        [2, 4],
        [8, 16],
      ]),
    ).toBe(true);
    expect(
      isGameOver([
        [2, 2],
        [8, 16],
      ]),
    ).toBe(false);
  });

  it("replays the same board from the same unsigned seed", () => {
    const first = createInitialBoardSeeded(new XorShift32(4_294_967_291));
    const second = createInitialBoardSeeded(new XorShift32(4_294_967_291));
    expect(first).toEqual(second);
    expect(first.flat().filter(Boolean)).toHaveLength(2);
  });
});
