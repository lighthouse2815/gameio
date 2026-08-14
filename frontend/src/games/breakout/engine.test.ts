import { describe, expect, it } from "vitest";
import {
  BreakoutEngine,
  BREAKOUT_BRICK_COLUMNS,
  BREAKOUT_BRICK_ROWS,
  sameBreakoutState,
  type BreakoutDirection,
} from "@/games/breakout/engine";

describe("breakout engine", () => {
  it("creates the same board and launch vector from the same seed", () => {
    const first = new BreakoutEngine(7_936).state();
    const second = new BreakoutEngine(7_936).state();

    expect(sameBreakoutState(first, second)).toBe(true);
    expect(first.bricks).toHaveLength(
      BREAKOUT_BRICK_COLUMNS * BREAKOUT_BRICK_ROWS,
    );
    expect(first.bricks.every(Boolean)).toBe(true);
  });

  it("moves the paddle without leaving the arena", () => {
    const engine = new BreakoutEngine(42);
    const initial = engine.state();

    const moved = engine.step("L");
    expect(moved.paddleX).toBeLessThan(initial.paddleX);
    for (let tick = 0; tick < 200; tick += 1) engine.step("L");
    expect(engine.state().paddleX).toBe(0);
  });

  it("replays a deterministic input stream", () => {
    const actions: BreakoutDirection[] = Array.from(
      { length: 600 },
      (_, index) => (index % 120 < 40 ? "L" : index % 120 < 80 ? "R" : "N"),
    );
    const first = new BreakoutEngine(2026);
    const second = new BreakoutEngine(2026);
    actions.forEach((action) => {
      if (!first.terminal()) first.step(action);
      if (!second.terminal()) second.step(action);
    });
    expect(sameBreakoutState(first.state(), second.state())).toBe(true);
  });
});
