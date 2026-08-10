import { describe, expect, it } from "vitest";
import {
  cloneSnakeState,
  createSnakeState,
  queueDirection,
  SnakeXorShift32,
  snakeActionForDirection,
  stepSnake,
  type SnakeState,
} from "@/games/snake/engine";

describe("snake engine", () => {
  it("matches the server xorshift32 float sequence", () => {
    const random = new SnakeXorShift32(0x12345678);
    expect(random.next()).toBe(2274908837 / 4_294_967_296);
    expect(random.next()).toBe(358294691 / 4_294_967_296);
    expect(random.next()).toBe(1210119364 / 4_294_967_296);
  });

  it("records the effective uppercase direction for each tick", () => {
    const random = new SnakeXorShift32(42);
    let state = createSnakeState(10, 8, () => random.next());
    state = queueDirection(state, "down");
    const action = snakeActionForDirection(state.queuedDirection);
    const next = stepSnake(state, () => random.next());
    expect(action).toBe("DOWN");
    expect(next.direction).toBe("down");
  });

  it("clones server state without retaining mutable point references", () => {
    const state = createSnakeState(10, 8, () => 0);
    const clone = cloneSnakeState(state);
    expect(clone).toEqual(state);
    expect(clone.body).not.toBe(state.body);
    expect(clone.body[0]).not.toBe(state.body[0]);
    expect(clone.food).not.toBe(state.food);
  });

  it("moves one cell per tick", () => {
    const state = createSnakeState(10, 8, () => 0);
    const next = stepSnake(state, () => 0);
    expect(next.body[0]).toEqual({
      x: state.body[0].x + 1,
      y: state.body[0].y,
    });
    expect(next.body).toHaveLength(state.body.length);
  });

  it("rejects an immediate reverse direction", () => {
    const state = createSnakeState(10, 8, () => 0);
    expect(queueDirection(state, "left")).toBe(state);
    expect(queueDirection(state, "up").queuedDirection).toBe("up");
  });

  it("grows, scores, and accelerates after eating", () => {
    const base = createSnakeState(10, 8, () => 0);
    const state: SnakeState = {
      ...base,
      food: { x: base.body[0].x + 1, y: base.body[0].y },
    };
    const next = stepSnake(state, () => 0);
    expect(next.body).toHaveLength(state.body.length + 1);
    expect(next.score).toBe(10);
    expect(next.tickMs).toBeLessThan(state.tickMs);
  });

  it("ends the run on wall collision", () => {
    const state: SnakeState = {
      ...createSnakeState(4, 4, () => 0),
      body: [
        { x: 3, y: 1 },
        { x: 2, y: 1 },
        { x: 1, y: 1 },
      ],
      direction: "right",
      queuedDirection: "right",
      food: { x: 0, y: 0 },
    };
    expect(stepSnake(state).status).toBe("over");
  });
});
