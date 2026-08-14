import { describe, expect, it } from "vitest";
import {
  MinesweeperEngine,
  MINESWEEPER_COLUMNS,
  MINESWEEPER_MINE_COUNT,
  MINESWEEPER_ROWS,
  sameMinesweeperState,
} from "@/games/minesweeper/engine";

describe("minesweeper engine", () => {
  it("keeps the first revealed cell safe", () => {
    const engine = new MinesweeperEngine(42);
    const result = engine.reveal(40);

    expect(result.changed).toBe(true);
    expect(result.state.cells[40].revealed).toBe(true);
    expect(result.state.cells[40].adjacent).toBeGreaterThanOrEqual(0);
    expect(result.state.status).not.toBe("lost");
  });

  it("reproduces the same public board from the same seed and actions", () => {
    const first = new MinesweeperEngine(7_936);
    const second = new MinesweeperEngine(7_936);
    [40, 0, 8, 72, 80, 12, 30].forEach((index) => {
      if (!first.terminal()) first.reveal(index);
      if (!second.terminal()) second.reveal(index);
    });
    expect(sameMinesweeperState(first.state(), second.state())).toBe(true);
  });

  it("exposes the expected beginner board dimensions", () => {
    const state = new MinesweeperEngine(1).state();
    expect(state.cells).toHaveLength(MINESWEEPER_ROWS * MINESWEEPER_COLUMNS);
    expect(state.mineCount).toBe(MINESWEEPER_MINE_COUNT);
  });
});
