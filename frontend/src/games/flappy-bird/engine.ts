export const FLAPPY_WIDTH = 640;
export const FLAPPY_HEIGHT = 480;
export const FLAPPY_TICK_MS = 50;
export const FLAPPY_BIRD_X = 160;
export const FLAPPY_BIRD_HALF_WIDTH = 16;
export const FLAPPY_BIRD_HALF_HEIGHT = 12;
export const FLAPPY_PIPE_WIDTH = 76;
export const FLAPPY_PIPE_GAP = 168;

const FIXED_POINT_SCALE = 100;
const GRAVITY = 55;
const FLAP_VELOCITY = -760;
const MAX_FALL_VELOCITY = 900;
const PIPE_SPEED = 4;
const FIRST_PIPE_X = 520;
const PIPE_SPACING = 260;
const INITIAL_PIPE_COUNT = 3;
const MIN_GAP_CENTER = 128;
const MAX_GAP_CENTER = 352;

export type FlappyAction = "FLAP" | "WAIT";
export type FlappyStatus = "playing" | "over";

export type FlappyPipe = {
  x: number;
  gapCenter: number;
  passed: boolean;
};

export type FlappyState = {
  width: number;
  height: number;
  birdX: number;
  birdY: number;
  birdVelocity: number;
  pipes: FlappyPipe[];
  score: number;
  tick: number;
  tickMs: number;
  status: FlappyStatus;
};

export class FlappyXorShift32 {
  private state: number;

  constructor(seed: number) {
    this.state = (seed >>> 0) || 0x6d2b79f5;
  }

  nextIndex(bound: number) {
    if (!Number.isInteger(bound) || bound <= 0) {
      throw new Error("Bound must be a positive integer");
    }
    let value = this.state;
    value ^= value << 13;
    value ^= value >>> 17;
    value ^= value << 5;
    this.state = value >>> 0;
    return Math.floor((this.state * bound) / 4_294_967_296);
  }
}
export function cloneFlappyState(state: FlappyState): FlappyState {
  return {
    ...state,
    pipes: state.pipes.map((pipe) => ({ ...pipe })),
  };
}

export function sameFlappyState(left: FlappyState, right: FlappyState) {
  return (
    left.width === right.width &&
    left.height === right.height &&
    left.birdX === right.birdX &&
    left.birdY === right.birdY &&
    left.birdVelocity === right.birdVelocity &&
    left.score === right.score &&
    left.tick === right.tick &&
    left.tickMs === right.tickMs &&
    left.status === right.status &&
    left.pipes.length === right.pipes.length &&
    left.pipes.every((pipe, index) => {
      const candidate = right.pipes[index];
      return (
        pipe.x === candidate?.x &&
        pipe.gapCenter === candidate.gapCenter &&
        pipe.passed === candidate.passed
      );
    })
  );
}

export function birdYInPixels(state: FlappyState) {
  return state.birdY / FIXED_POINT_SCALE;
}

export class FlappyEngine {
  private readonly random: FlappyXorShift32;
  private current: FlappyState;

  constructor(seed: number) {
    this.random = new FlappyXorShift32(seed);
    this.current = {
      width: FLAPPY_WIDTH,
      height: FLAPPY_HEIGHT,
      birdX: FLAPPY_BIRD_X,
      birdY: (FLAPPY_HEIGHT / 2) * FIXED_POINT_SCALE,
      birdVelocity: 0,
      pipes: Array.from({ length: INITIAL_PIPE_COUNT }, (_, index) => ({
        x: FIRST_PIPE_X + index * PIPE_SPACING,
        gapCenter: this.nextGapCenter(),
        passed: false,
      })),
      score: 0,
      tick: 0,
      tickMs: FLAPPY_TICK_MS,
      status: "playing",
    };
  }

  state() {
    return cloneFlappyState(this.current);
  }

  terminal() {
    return this.current.status === "over";
  }

  step(action: FlappyAction) {
    if (this.terminal()) return this.state();

    let birdVelocity =
      action === "FLAP" ? FLAP_VELOCITY : this.current.birdVelocity;
    birdVelocity = Math.min(MAX_FALL_VELOCITY, birdVelocity + GRAVITY);
    const birdY = this.current.birdY + birdVelocity;
    let score = this.current.score;
    let pipes = this.current.pipes.map((pipe) => {
      const moved = { ...pipe, x: pipe.x - PIPE_SPEED };
      if (!moved.passed && moved.x + FLAPPY_PIPE_WIDTH < FLAPPY_BIRD_X) {
        moved.passed = true;
        score += 1;
      }
      return moved;
    });

    while (pipes.length && pipes[0].x + FLAPPY_PIPE_WIDTH < 0) {
      pipes = pipes.slice(1);
      const lastX = pipes.at(-1)?.x ?? FIRST_PIPE_X;
      pipes.push({
        x: lastX + PIPE_SPACING,
        gapCenter: this.nextGapCenter(),
        passed: false,
      });
    }

    const hitBoundary =
      birdY - FLAPPY_BIRD_HALF_HEIGHT * FIXED_POINT_SCALE <= 0 ||
      birdY + FLAPPY_BIRD_HALF_HEIGHT * FIXED_POINT_SCALE >=
        FLAPPY_HEIGHT * FIXED_POINT_SCALE;
    const birdLeft = FLAPPY_BIRD_X - FLAPPY_BIRD_HALF_WIDTH;
    const birdRight = FLAPPY_BIRD_X + FLAPPY_BIRD_HALF_WIDTH;
    const birdTop = birdY - FLAPPY_BIRD_HALF_HEIGHT * FIXED_POINT_SCALE;
    const birdBottom = birdY + FLAPPY_BIRD_HALF_HEIGHT * FIXED_POINT_SCALE;
    const hitPipe = pipes.some((pipe) => {
      const overlapsHorizontally =
        birdRight > pipe.x && birdLeft < pipe.x + FLAPPY_PIPE_WIDTH;
      if (!overlapsHorizontally) return false;
      const gapTop =
        (pipe.gapCenter - FLAPPY_PIPE_GAP / 2) * FIXED_POINT_SCALE;
      const gapBottom =
        (pipe.gapCenter + FLAPPY_PIPE_GAP / 2) * FIXED_POINT_SCALE;
      return birdTop < gapTop || birdBottom > gapBottom;
    });

    this.current = {
      ...this.current,
      birdY,
      birdVelocity,
      pipes,
      score,
      tick: this.current.tick + 1,
      status: hitBoundary || hitPipe ? "over" : "playing",
    };
    return this.state();
  }

  private nextGapCenter() {
    return (
      MIN_GAP_CENTER +
      this.random.nextIndex(MAX_GAP_CENTER - MIN_GAP_CENTER + 1)
    );
  }
}
