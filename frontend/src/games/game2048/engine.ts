export type Board = number[][];
export type MoveDirection = "up" | "down" | "left" | "right";
export type RandomSource = () => number;
export type IntegerRandomSource = {
  nextInt: (bound: number) => number;
};

export type MoveResult = {
  board: Board;
  scoreDelta: number;
  moved: boolean;
};

export function createEmptyBoard(size = 4): Board {
  return Array.from({ length: size }, () => Array(size).fill(0) as number[]);
}

export function cloneBoard(board: Board): Board {
  return board.map((row) => [...row]);
}

export function spawnTile(
  board: Board,
  random: RandomSource = Math.random,
): Board {
  const next = cloneBoard(board);
  const emptyCells: Array<[number, number]> = [];
  next.forEach((row, rowIndex) => {
    row.forEach((value, columnIndex) => {
      if (value === 0) {
        emptyCells.push([rowIndex, columnIndex]);
      }
    });
  });
  if (!emptyCells.length) {
    return next;
  }
  const cellIndex = Math.min(
    emptyCells.length - 1,
    Math.floor(random() * emptyCells.length),
  );
  const [row, column] = emptyCells[cellIndex];
  next[row][column] = random() < 0.9 ? 2 : 4;
  return next;
}

export class XorShift32 implements IntegerRandomSource {
  private state: number;

  constructor(seed: number) {
    this.state = (seed >>> 0) || 0x6d2b79f5;
  }

  nextInt(bound: number) {
    if (!Number.isInteger(bound) || bound <= 0) {
      throw new Error("Random bound must be a positive integer.");
    }
    let value = this.state;
    value ^= value << 13;
    value ^= value >>> 17;
    value ^= value << 5;
    this.state = value >>> 0;
    return this.state % bound;
  }
}

export function spawnTileSeeded(
  board: Board,
  random: IntegerRandomSource,
): Board {
  const next = cloneBoard(board);
  const emptyCells: Array<[number, number]> = [];
  next.forEach((row, rowIndex) => {
    row.forEach((value, columnIndex) => {
      if (value === 0) emptyCells.push([rowIndex, columnIndex]);
    });
  });
  if (!emptyCells.length) return next;
  const [row, column] = emptyCells[random.nextInt(emptyCells.length)];
  next[row][column] = random.nextInt(10) < 9 ? 2 : 4;
  return next;
}

export function createInitialBoardSeeded(random: IntegerRandomSource): Board {
  return spawnTileSeeded(spawnTileSeeded(createEmptyBoard(), random), random);
}

export function createInitialBoard(random: RandomSource = Math.random): Board {
  return spawnTile(spawnTile(createEmptyBoard(), random), random);
}

function collapseLine(line: number[]) {
  const values = line.filter((value) => value !== 0);
  const merged: number[] = [];
  let scoreDelta = 0;

  for (let index = 0; index < values.length; index += 1) {
    if (values[index] === values[index + 1]) {
      const value = values[index] * 2;
      merged.push(value);
      scoreDelta += value;
      index += 1;
    } else {
      merged.push(values[index]);
    }
  }

  while (merged.length < line.length) {
    merged.push(0);
  }
  return { line: merged, scoreDelta };
}

function linesForDirection(board: Board, direction: MoveDirection) {
  const size = board.length;
  if (direction === "left" || direction === "right") {
    return board.map((row) =>
      direction === "right" ? [...row].reverse() : [...row],
    );
  }
  return Array.from({ length: size }, (_, column) => {
    const line = Array.from(
      { length: size },
      (__, row) => board[row][column],
    );
    return direction === "down" ? line.reverse() : line;
  });
}

function boardFromLines(
  lines: number[][],
  direction: MoveDirection,
): Board {
  if (direction === "left") {
    return lines;
  }
  if (direction === "right") {
    return lines.map((line) => [...line].reverse());
  }
  const normalized =
    direction === "down"
      ? lines.map((line) => [...line].reverse())
      : lines;
  return Array.from({ length: normalized.length }, (_, row) =>
    Array.from(
      { length: normalized.length },
      (__, column) => normalized[column][row],
    ),
  );
}

export function moveBoard(
  board: Board,
  direction: MoveDirection,
): MoveResult {
  let scoreDelta = 0;
  const movedLines = linesForDirection(board, direction).map((line) => {
    const result = collapseLine(line);
    scoreDelta += result.scoreDelta;
    return result.line;
  });
  const next = boardFromLines(movedLines, direction);
  const moved = next.some((row, rowIndex) =>
    row.some((value, columnIndex) => value !== board[rowIndex][columnIndex]),
  );
  return { board: next, scoreDelta, moved };
}

export function isGameOver(board: Board) {
  const size = board.length;
  for (let row = 0; row < size; row += 1) {
    for (let column = 0; column < size; column += 1) {
      const value = board[row][column];
      if (value === 0) return false;
      if (row + 1 < size && board[row + 1][column] === value) return false;
      if (column + 1 < size && board[row][column + 1] === value) return false;
    }
  }
  return true;
}

export function hasWon(board: Board, target = 2048) {
  return board.some((row) => row.some((value) => value >= target));
}
