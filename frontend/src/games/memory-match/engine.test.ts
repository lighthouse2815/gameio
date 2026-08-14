import { describe, expect, it } from "vitest";
import {
  MemoryMatchEngine,
  MEMORY_PAIR_COUNT,
  sameMemoryState,
} from "@/games/memory-match/engine";

describe("memory match engine", () => {
  it("starts with every card hidden", () => {
    const state = new MemoryMatchEngine(42).state();
    expect(state.cells).toHaveLength(MEMORY_PAIR_COUNT * 2);
    expect(state.cells.every((cell) => !cell.revealed && cell.value === null)).toBe(
      true,
    );
  });

  it("reproduces the same selections from the same seed", () => {
    const first = new MemoryMatchEngine(2026);
    const second = new MemoryMatchEngine(2026);
    [0, 1, 2, 3, 4, 5].forEach((index) => {
      first.select(index);
      second.select(index);
    });
    expect(sameMemoryState(first.state(), second.state())).toBe(true);
  });

  it("clears a mismatched pair before the next selection", () => {
    const engine = new MemoryMatchEngine(7_936);
    engine.select(0);
    let secondIndex = 1;
    while (secondIndex < 16) {
      const probe = new MemoryMatchEngine(7_936);
      probe.select(0);
      const result = probe.select(secondIndex).state;
      if (result.pendingMismatch) break;
      secondIndex += 1;
    }
    const mismatch = engine.select(secondIndex).state;
    expect(mismatch.pendingMismatch).toBe(true);

    engine.clearMismatch();
    expect(engine.state().selected).toEqual([]);
    expect(engine.state().cells.every((cell) => !cell.revealed)).toBe(true);
  });
});
