import { describe, expect, it } from "vitest";
import {
  birdYInPixels,
  FlappyEngine,
  FLAPPY_BIRD_HALF_WIDTH,
  FLAPPY_PIPE_GAP,
  FLAPPY_PIPE_SPEED,
  FLAPPY_PIPE_WIDTH,
  FLAPPY_TICK_MS,
  projectFlappyState,
  sameFlappyState,
  type FlappyAction,
} from "@/games/flappy-bird/engine";

function finishWithoutFlapping(seed: number) {
  const engine = new FlappyEngine(seed);
  const actions: FlappyAction[] = [];
  while (!engine.terminal() && actions.length < 100) {
    actions.push("WAIT");
    engine.step("WAIT");
  }
  return { actions, state: engine.state() };
}

function finishAfterScoring(seed: number, targetScore: number) {
  const engine = new FlappyEngine(seed);
  const actions: FlappyAction[] = [];
  while (!engine.terminal() && actions.length < 1_000) {
    const state = engine.state();
    const nextPipe = state.pipes.find(
      (pipe) =>
        pipe.x + FLAPPY_PIPE_WIDTH >=
        state.birdX - FLAPPY_BIRD_HALF_WIDTH,
    );
    const trackingTarget = nextPipe?.gapCenter ?? state.height / 2;
    const shouldFlap =
      state.score < targetScore &&
      birdYInPixels(state) > trackingTarget - 20 &&
      state.birdVelocity > 50;
    const action: FlappyAction = shouldFlap ? "FLAP" : "WAIT";
    actions.push(action);
    engine.step(action);
  }
  return { actions, state: engine.state() };
}

describe("flappy bird engine", () => {
  it("creates deterministic pipe gaps from the session seed", () => {
    const first = new FlappyEngine(7_936).state();
    const second = new FlappyEngine(7_936).state();

    expect(sameFlappyState(first, second)).toBe(true);
    expect(first.pipes).toHaveLength(3);
    expect(first.pipes.map((pipe) => pipe.x)).toEqual([520, 780, 1040]);
    expect(first.pipes.every((pipe) => pipe.gapCenter >= 128)).toBe(true);
    expect(first.pipes.every((pipe) => pipe.gapCenter <= 352)).toBe(true);
    expect(first.tickMs).toBe(FLAPPY_TICK_MS);
  });

  it("applies one flap on a fixed simulation tick", () => {
    const engine = new FlappyEngine(42);
    const before = engine.state();
    const after = engine.step("FLAP");

    expect(after.tick).toBe(1);
    expect(after.birdVelocity).toBeLessThan(0);
    expect(birdYInPixels(after)).toBeLessThan(birdYInPixels(before));
    expect(after.status).toBe("playing");
  });

  it("projects smooth render frames without mutating authoritative state", () => {
    const engine = new FlappyEngine(42);
    const authoritative = engine.state();
    const halfway = projectFlappyState(authoritative, "WAIT", 0.5);
    const projectedTick = projectFlappyState(authoritative, "WAIT", 1);
    const nextTick = engine.step("WAIT");

    expect(authoritative.tick).toBe(0);
    expect(authoritative.pipes[0].x).toBe(520);
    expect(halfway.tick).toBe(0);
    expect(FLAPPY_PIPE_SPEED).toBe(5);
    expect(halfway.pipes[0].x).toBe(517.5);
    expect(nextTick.pipes[0].x).toBe(515);
    expect(projectedTick.birdY).toBe(nextTick.birdY);
    expect(projectedTick.birdVelocity).toBe(nextTick.birdVelocity);
    expect(projectedTick.pipes.map((pipe) => pipe.x)).toEqual(
      nextTick.pipes.map((pipe) => pipe.x),
    );
  });

  it("clamps render projection progress to one simulation tick", () => {
    const state = new FlappyEngine(42).state();

    expect(sameFlappyState(projectFlappyState(state, "WAIT", -1), state)).toBe(
      true,
    );
    expect(projectFlappyState(state, "FLAP", 2).birdY).toBe(
      new FlappyEngine(42).step("FLAP").birdY,
    );
  });

  it("reaches a deterministic terminal state without flapping", () => {
    const first = finishWithoutFlapping(99);
    const second = finishWithoutFlapping(99);

    expect(first.actions.length).toBeGreaterThan(1);
    expect(first.actions).toEqual(second.actions);
    expect(sameFlappyState(first.state, second.state)).toBe(true);
    expect(first.state.status).toBe("over");
    expect(first.state.score).toBe(0);
  });

  it("scores a gate exactly once during a controlled flight", () => {
    const replay = finishAfterScoring(42, 2);

    expect(replay.actions.length).toBeLessThan(1_000);
    expect(replay.state.status).toBe("over");
    expect(replay.state.score).toBe(2);
  });

  it("keeps every generated opening large enough for the bird", () => {
    expect(FLAPPY_PIPE_GAP).toBeGreaterThan(80);
  });
});
