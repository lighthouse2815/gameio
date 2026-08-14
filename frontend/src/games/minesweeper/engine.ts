import { SeededRandom } from "@/games/core/seeded-random";

export const MINESWEEPER_ROWS = 9;
export const MINESWEEPER_COLUMNS = 9;
export const MINESWEEPER_MINE_COUNT = 10;

export type MinesweeperStatus = "playing" | "won" | "lost";
export type MinesweeperCell = {
  revealed: boolean;
  flagged: boolean;
  adjacent: number;
};
export type MinesweeperState = {
  rows: number;
  columns: number;
  mineCount: number;
  cells: MinesweeperCell[];
  revealedCount: number;
  score: number;
  moves: number;
  status: MinesweeperStatus;
};
export type MinesweeperReplayAction = `R:${number}`;

function emptyCells() {
  return Array.from(
    { length: MINESWEEPER_ROWS * MINESWEEPER_COLUMNS },
    (): MinesweeperCell => ({ revealed: false, flagged: false, adjacent: 0 }),
  );
}

function cloneState(state: MinesweeperState): MinesweeperState {
  return { ...state, cells: state.cells.map((cell) => ({ ...cell })) };
}

export function sameMinesweeperState(
  left: MinesweeperState,
  right: MinesweeperState,
) {
  return (
    left.rows === right.rows &&
    left.columns === right.columns &&
    left.mineCount === right.mineCount &&
    left.revealedCount === right.revealedCount &&
    left.score === right.score &&
    left.moves === right.moves &&
    left.status === right.status &&
    left.cells.length === right.cells.length &&
    left.cells.every((cell, index) => {
      const other = right.cells[index];
      return (
        cell.revealed === other?.revealed &&
        cell.flagged === other.flagged &&
        cell.adjacent === other.adjacent
      );
    })
  );
}

export class MinesweeperEngine {
  private readonly seed: number;
  private mines: boolean[] | null = null;
  private current: MinesweeperState;

  constructor(seed: number) {
    this.seed = seed;
    this.current = {
      rows: MINESWEEPER_ROWS,
      columns: MINESWEEPER_COLUMNS,
      mineCount: MINESWEEPER_MINE_COUNT,
      cells: emptyCells(),
      revealedCount: 0,
      score: 0,
      moves: 0,
      status: "playing",
    };
  }

  state() {
    return cloneState(this.current);
  }

  terminal() {
    return this.current.status !== "playing";
  }

  reveal(index: number) {
    if (
      this.terminal() ||
      !Number.isInteger(index) ||
      index < 0 ||
      index >= this.current.cells.length ||
      this.current.cells[index].revealed ||
      this.current.cells[index].flagged
    ) {
      return { changed: false, state: this.state() };
    }
    if (!this.mines) this.placeMines(index);
    const mines = this.mines;
    if (!mines) {
      throw new Error("Minefield initialization failed");
    }
    const cells = this.current.cells.map((cell) => ({ ...cell }));
    let revealedCount = this.current.revealedCount;
    let status: MinesweeperStatus = "playing";

    if (mines[index]) {
      cells[index] = { revealed: true, flagged: false, adjacent: -1 };
      status = "lost";
    } else {
      const queue = [index];
      const queued = new Set(queue);
      while (queue.length) {
        const candidate = queue.shift()!;
        if (cells[candidate].revealed || mines[candidate]) continue;
        const adjacent = this.adjacentMineCount(candidate);
        cells[candidate] = { revealed: true, flagged: false, adjacent };
        revealedCount += 1;
        if (adjacent === 0) {
          this.neighbors(candidate).forEach((neighbor) => {
            if (!queued.has(neighbor) && !mines[neighbor]) {
              queued.add(neighbor);
              queue.push(neighbor);
            }
          });
        }
      }
      if (
        revealedCount ===
        MINESWEEPER_ROWS * MINESWEEPER_COLUMNS - MINESWEEPER_MINE_COUNT
      ) {
        status = "won";
      }
    }

    const moves = this.current.moves + 1;
    const score = revealedCount * 10 + (status === "won" ? 500 : 0);
    this.current = {
      ...this.current,
      cells,
      revealedCount,
      score,
      moves,
      status,
    };
    return { changed: true, state: this.state() };
  }

  toggleFlag(index: number) {
    if (
      this.terminal() ||
      !Number.isInteger(index) ||
      index < 0 ||
      index >= this.current.cells.length ||
      this.current.cells[index].revealed
    ) {
      return this.state();
    }
    const cells = this.current.cells.map((cell, cellIndex) =>
      cellIndex === index ? { ...cell, flagged: !cell.flagged } : { ...cell },
    );
    this.current = { ...this.current, cells };
    return this.state();
  }

  private placeMines(firstIndex: number) {
    const random = new SeededRandom(this.seed);
    const candidates = Array.from(
      { length: MINESWEEPER_ROWS * MINESWEEPER_COLUMNS },
      (_, index) => index,
    ).filter((index) => index !== firstIndex);
    for (let index = candidates.length - 1; index > 0; index -= 1) {
      const swapIndex = random.nextIndex(index + 1);
      [candidates[index], candidates[swapIndex]] = [
        candidates[swapIndex],
        candidates[index],
      ];
    }
    this.mines = Array.from(
      { length: MINESWEEPER_ROWS * MINESWEEPER_COLUMNS },
      () => false,
    );
    candidates
      .slice(0, MINESWEEPER_MINE_COUNT)
      .forEach((index) => (this.mines![index] = true));
  }

  private neighbors(index: number) {
    const row = Math.floor(index / MINESWEEPER_COLUMNS);
    const column = index % MINESWEEPER_COLUMNS;
    const neighbors: number[] = [];
    for (let rowDelta = -1; rowDelta <= 1; rowDelta += 1) {
      for (let columnDelta = -1; columnDelta <= 1; columnDelta += 1) {
        if (rowDelta === 0 && columnDelta === 0) continue;
        const candidateRow = row + rowDelta;
        const candidateColumn = column + columnDelta;
        if (
          candidateRow >= 0 &&
          candidateRow < MINESWEEPER_ROWS &&
          candidateColumn >= 0 &&
          candidateColumn < MINESWEEPER_COLUMNS
        ) {
          neighbors.push(candidateRow * MINESWEEPER_COLUMNS + candidateColumn);
        }
      }
    }
    return neighbors;
  }

  private adjacentMineCount(index: number) {
    return this.neighbors(index).filter((neighbor) => this.mines?.[neighbor])
      .length;
  }
}
