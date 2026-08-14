import { SeededRandom } from "@/games/core/seeded-random";

export const BREAKOUT_WIDTH = 640;
export const BREAKOUT_HEIGHT = 480;
export const BREAKOUT_TICK_MS = 1000 / 60;
export const BREAKOUT_PADDLE_WIDTH = 104;
export const BREAKOUT_PADDLE_HEIGHT = 14;
export const BREAKOUT_PADDLE_Y = 438;
export const BREAKOUT_BALL_RADIUS = 8;
export const BREAKOUT_BRICK_COLUMNS = 7;
export const BREAKOUT_BRICK_ROWS = 4;
export const BREAKOUT_BRICK_WIDTH = 72;
export const BREAKOUT_BRICK_HEIGHT = 22;
export const BREAKOUT_BRICK_GAP = 8;
export const BREAKOUT_BRICK_LEFT = 44;
export const BREAKOUT_BRICK_TOP = 64;

const PADDLE_SPEED = 7;
const INITIAL_LIVES = 3;
const BALL_SPEED_X = 3;
const BALL_SPEED_Y = -4;

export type BreakoutDirection = "L" | "R" | "N";
export type BreakoutStatus = "playing" | "won" | "lost";

export type BreakoutState = {
  width: number;
  height: number;
  paddleX: number;
  ballX: number;
  ballY: number;
  velocityX: number;
  velocityY: number;
  bricks: boolean[];
  score: number;
  lives: number;
  tick: number;
  status: BreakoutStatus;
};

export function breakoutBrickRect(index: number) {
  const row = Math.floor(index / BREAKOUT_BRICK_COLUMNS);
  const column = index % BREAKOUT_BRICK_COLUMNS;
  return {
    x: BREAKOUT_BRICK_LEFT + column * (BREAKOUT_BRICK_WIDTH + BREAKOUT_BRICK_GAP),
    y: BREAKOUT_BRICK_TOP + row * (BREAKOUT_BRICK_HEIGHT + BREAKOUT_BRICK_GAP),
    width: BREAKOUT_BRICK_WIDTH,
    height: BREAKOUT_BRICK_HEIGHT,
  };
}

function cloneState(state: BreakoutState): BreakoutState {
  return { ...state, bricks: [...state.bricks] };
}

export function sameBreakoutState(left: BreakoutState, right: BreakoutState) {
  return (
    left.width === right.width &&
    left.height === right.height &&
    left.paddleX === right.paddleX &&
    left.ballX === right.ballX &&
    left.ballY === right.ballY &&
    left.velocityX === right.velocityX &&
    left.velocityY === right.velocityY &&
    left.score === right.score &&
    left.lives === right.lives &&
    left.tick === right.tick &&
    left.status === right.status &&
    left.bricks.length === right.bricks.length &&
    left.bricks.every((alive, index) => alive === right.bricks[index])
  );
}

export class BreakoutEngine {
  private current: BreakoutState;

  constructor(seed: number) {
    const random = new SeededRandom(seed);
    const horizontalDirection = random.nextIndex(2) === 0 ? -1 : 1;
    const offset = random.nextIndex(81) - 40;
    this.current = {
      width: BREAKOUT_WIDTH,
      height: BREAKOUT_HEIGHT,
      paddleX: (BREAKOUT_WIDTH - BREAKOUT_PADDLE_WIDTH) / 2,
      ballX: BREAKOUT_WIDTH / 2 + offset,
      ballY: 334,
      velocityX: BALL_SPEED_X * horizontalDirection,
      velocityY: BALL_SPEED_Y,
      bricks: Array.from(
        { length: BREAKOUT_BRICK_COLUMNS * BREAKOUT_BRICK_ROWS },
        () => true,
      ),
      score: 0,
      lives: INITIAL_LIVES,
      tick: 0,
      status: "playing",
    };
  }

  state() {
    return cloneState(this.current);
  }

  terminal() {
    return this.current.status !== "playing";
  }

  step(direction: BreakoutDirection) {
    if (this.terminal()) return this.state();

    const paddleDelta =
      direction === "L" ? -PADDLE_SPEED : direction === "R" ? PADDLE_SPEED : 0;
    const paddleX = Math.max(
      0,
      Math.min(
        BREAKOUT_WIDTH - BREAKOUT_PADDLE_WIDTH,
        this.current.paddleX + paddleDelta,
      ),
    );
    let velocityX = this.current.velocityX;
    let velocityY = this.current.velocityY;
    let ballX = this.current.ballX + velocityX;
    let ballY = this.current.ballY + velocityY;
    let score = this.current.score;
    let lives = this.current.lives;
    let status: BreakoutStatus = "playing";
    const bricks = [...this.current.bricks];

    if (ballX <= BREAKOUT_BALL_RADIUS) {
      ballX = BREAKOUT_BALL_RADIUS;
      velocityX = Math.abs(velocityX);
    } else if (ballX >= BREAKOUT_WIDTH - BREAKOUT_BALL_RADIUS) {
      ballX = BREAKOUT_WIDTH - BREAKOUT_BALL_RADIUS;
      velocityX = -Math.abs(velocityX);
    }
    if (ballY <= BREAKOUT_BALL_RADIUS) {
      ballY = BREAKOUT_BALL_RADIUS;
      velocityY = Math.abs(velocityY);
    }

    const reachedPaddle =
      velocityY > 0 &&
      this.current.ballY + BREAKOUT_BALL_RADIUS <= BREAKOUT_PADDLE_Y &&
      ballY + BREAKOUT_BALL_RADIUS >= BREAKOUT_PADDLE_Y &&
      ballX + BREAKOUT_BALL_RADIUS >= paddleX &&
      ballX - BREAKOUT_BALL_RADIUS <= paddleX + BREAKOUT_PADDLE_WIDTH;
    if (reachedPaddle) {
      ballY = BREAKOUT_PADDLE_Y - BREAKOUT_BALL_RADIUS;
      velocityY = -Math.abs(velocityY);
      const paddleCenter = paddleX + BREAKOUT_PADDLE_WIDTH / 2;
      const influence = Math.trunc(
        ((ballX - paddleCenter) * 5) / (BREAKOUT_PADDLE_WIDTH / 2),
      );
      velocityX = Math.max(-6, Math.min(6, velocityX + influence));
      if (velocityX === 0) velocityX = direction === "L" ? -2 : 2;
    }

    for (let index = 0; index < bricks.length; index += 1) {
      if (!bricks[index]) continue;
      const brick = breakoutBrickRect(index);
      const overlaps =
        ballX + BREAKOUT_BALL_RADIUS >= brick.x &&
        ballX - BREAKOUT_BALL_RADIUS <= brick.x + brick.width &&
        ballY + BREAKOUT_BALL_RADIUS >= brick.y &&
        ballY - BREAKOUT_BALL_RADIUS <= brick.y + brick.height;
      if (!overlaps) continue;
      bricks[index] = false;
      score += 50;
      velocityY = -velocityY;
      ballY = this.current.ballY + velocityY;
      break;
    }

    if (bricks.every((alive) => !alive)) {
      score += lives * 250;
      status = "won";
    } else if (ballY - BREAKOUT_BALL_RADIUS > BREAKOUT_HEIGHT) {
      lives -= 1;
      if (lives <= 0) {
        status = "lost";
      } else {
        ballX = BREAKOUT_WIDTH / 2;
        ballY = 334;
        velocityX = this.current.tick % 2 === 0 ? BALL_SPEED_X : -BALL_SPEED_X;
        velocityY = BALL_SPEED_Y;
      }
    }

    this.current = {
      ...this.current,
      paddleX,
      ballX,
      ballY,
      velocityX,
      velocityY,
      bricks,
      score,
      lives,
      tick: this.current.tick + 1,
      status,
    };
    return this.state();
  }
}
