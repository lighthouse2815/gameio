export type Point = { x: number; y: number };
export type SnakeDirection = "up" | "down" | "left" | "right";
export type SnakeStatus = "playing" | "over" | "won";
export type RandomSource = () => number;

export type SnakeState = {
  width: number;
  height: number;
  body: Point[];
  direction: SnakeDirection;
  queuedDirection: SnakeDirection;
  food: Point | null;
  score: number;
  tickMs: number;
  status: SnakeStatus;
};

const OPPOSITE: Record<SnakeDirection, SnakeDirection> = {
  up: "down",
  down: "up",
  left: "right",
  right: "left",
};

export class SnakeXorShift32 {
  private state: number;

  constructor(seed: number) {
    this.state = (seed >>> 0) || 0x6d2b79f5;
  }

  next() {
    let value = this.state;
    value ^= value << 13;
    value ^= value >>> 17;
    value ^= value << 5;
    this.state = value >>> 0;
    return this.state / 4_294_967_296;
  }
}

export function snakeActionForDirection(direction: SnakeDirection) {
  return direction.toUpperCase() as Uppercase<SnakeDirection>;
}

export function cloneSnakeState(state: SnakeState): SnakeState {
  return {
    ...state,
    body: state.body.map((segment) => ({ ...segment })),
    food: state.food ? { ...state.food } : null,
  };
}

function samePoint(left: Point, right: Point) {
  return left.x === right.x && left.y === right.y;
}

export function placeFood(
  width: number,
  height: number,
  body: Point[],
  random: RandomSource = Math.random,
): Point | null {
  const open: Point[] = [];
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const point = { x, y };
      if (!body.some((segment) => samePoint(segment, point))) {
        open.push(point);
      }
    }
  }
  if (!open.length) return null;
  return open[Math.min(open.length - 1, Math.floor(random() * open.length))];
}

export function createSnakeState(
  width = 20,
  height = 15,
  random: RandomSource = Math.random,
): SnakeState {
  const centerX = Math.floor(width / 2);
  const centerY = Math.floor(height / 2);
  const body = [
    { x: centerX, y: centerY },
    { x: centerX - 1, y: centerY },
    { x: centerX - 2, y: centerY },
  ];
  return {
    width,
    height,
    body,
    direction: "right",
    queuedDirection: "right",
    food: placeFood(width, height, body, random),
    score: 0,
    tickMs: 150,
    status: "playing",
  };
}

export function queueDirection(
  state: SnakeState,
  direction: SnakeDirection,
): SnakeState {
  if (
    state.status !== "playing" ||
    direction === OPPOSITE[state.direction] ||
    state.queuedDirection !== state.direction
  ) {
    return state;
  }
  return { ...state, queuedDirection: direction };
}

function nextHead(head: Point, direction: SnakeDirection): Point {
  if (direction === "up") return { x: head.x, y: head.y - 1 };
  if (direction === "down") return { x: head.x, y: head.y + 1 };
  if (direction === "left") return { x: head.x - 1, y: head.y };
  return { x: head.x + 1, y: head.y };
}

export function stepSnake(
  state: SnakeState,
  random: RandomSource = Math.random,
): SnakeState {
  if (state.status !== "playing") return state;
  const direction = state.queuedDirection;
  const head = nextHead(state.body[0], direction);
  const ate = state.food ? samePoint(head, state.food) : false;
  const collisionBody = ate ? state.body : state.body.slice(0, -1);
  const hitWall =
    head.x < 0 ||
    head.y < 0 ||
    head.x >= state.width ||
    head.y >= state.height;
  const hitSelf = collisionBody.some((segment) => samePoint(segment, head));
  if (hitWall || hitSelf) {
    return {
      ...state,
      direction,
      queuedDirection: direction,
      status: "over",
    };
  }

  const body = ate
    ? [head, ...state.body]
    : [head, ...state.body.slice(0, -1)];
  const score = state.score + (ate ? 10 : 0);
  const food = ate
    ? placeFood(state.width, state.height, body, random)
    : state.food;
  return {
    ...state,
    body,
    direction,
    queuedDirection: direction,
    food,
    score,
    tickMs: Math.max(55, 150 - Math.floor(score / 10) * 6),
    status: food ? "playing" : "won",
  };
}
