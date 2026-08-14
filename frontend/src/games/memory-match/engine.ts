import { SeededRandom } from "@/games/core/seeded-random";

export const MEMORY_COLUMNS = 4;
export const MEMORY_ROWS = 4;
export const MEMORY_PAIR_COUNT = 8;

export type MemoryStatus = "playing" | "won";
export type MemoryCell = {
  value: number | null;
  revealed: boolean;
  matched: boolean;
};
export type MemoryState = {
  rows: number;
  columns: number;
  cells: MemoryCell[];
  selected: number[];
  matchedPairs: number;
  moves: number;
  score: number;
  pendingMismatch: boolean;
  status: MemoryStatus;
};
export type MemoryReplayAction = `S:${number}`;

function cloneState(state: MemoryState): MemoryState {
  return {
    ...state,
    cells: state.cells.map((cell) => ({ ...cell })),
    selected: [...state.selected],
  };
}

export function sameMemoryState(left: MemoryState, right: MemoryState) {
  return (
    left.rows === right.rows &&
    left.columns === right.columns &&
    left.matchedPairs === right.matchedPairs &&
    left.moves === right.moves &&
    left.score === right.score &&
    left.pendingMismatch === right.pendingMismatch &&
    left.status === right.status &&
    left.selected.join(",") === right.selected.join(",") &&
    left.cells.length === right.cells.length &&
    left.cells.every((cell, index) => {
      const other = right.cells[index];
      return (
        cell.value === other?.value &&
        cell.revealed === other.revealed &&
        cell.matched === other.matched
      );
    })
  );
}

export class MemoryMatchEngine {
  private readonly deck: number[];
  private current: MemoryState;

  constructor(seed: number) {
    const random = new SeededRandom(seed);
    this.deck = Array.from({ length: MEMORY_PAIR_COUNT }, (_, value) => [value, value])
      .flat();
    for (let index = this.deck.length - 1; index > 0; index -= 1) {
      const swapIndex = random.nextIndex(index + 1);
      [this.deck[index], this.deck[swapIndex]] = [
        this.deck[swapIndex],
        this.deck[index],
      ];
    }
    this.current = {
      rows: MEMORY_ROWS,
      columns: MEMORY_COLUMNS,
      cells: this.deck.map(() => ({ value: null, revealed: false, matched: false })),
      selected: [],
      matchedPairs: 0,
      moves: 0,
      score: 0,
      pendingMismatch: false,
      status: "playing",
    };
  }

  state() {
    return cloneState(this.current);
  }

  terminal() {
    return this.current.status === "won";
  }

  clearMismatch() {
    if (!this.current.pendingMismatch) return this.state();
    const selected = new Set(this.current.selected);
    const cells = this.current.cells.map((cell, index) =>
      selected.has(index) && !cell.matched
        ? { value: null, revealed: false, matched: false }
        : { ...cell },
    );
    this.current = {
      ...this.current,
      cells,
      selected: [],
      pendingMismatch: false,
    };
    return this.state();
  }

  select(index: number) {
    if (this.current.pendingMismatch) this.clearMismatch();
    if (
      this.terminal() ||
      !Number.isInteger(index) ||
      index < 0 ||
      index >= this.current.cells.length ||
      this.current.cells[index].revealed ||
      this.current.cells[index].matched
    ) {
      return { changed: false, state: this.state() };
    }

    const cells = this.current.cells.map((cell) => ({ ...cell }));
    cells[index] = { value: this.deck[index], revealed: true, matched: false };
    const selected = [...this.current.selected, index];
    let matchedPairs = this.current.matchedPairs;
    let moves = this.current.moves;
    let pendingMismatch = false;
    let nextSelected = selected;

    if (selected.length === 2) {
      moves += 1;
      const [first, second] = selected;
      if (this.deck[first] === this.deck[second]) {
        cells[first].matched = true;
        cells[second].matched = true;
        matchedPairs += 1;
        nextSelected = [];
      } else {
        pendingMismatch = true;
      }
    }

    const status: MemoryStatus =
      matchedPairs === MEMORY_PAIR_COUNT ? "won" : "playing";
    const score =
      matchedPairs * 100 +
      (status === "won" ? Math.max(0, 500 - moves * 10) : 0);
    this.current = {
      ...this.current,
      cells,
      selected: nextSelected,
      matchedPairs,
      moves,
      score,
      pendingMismatch,
      status,
    };
    return { changed: true, state: this.state() };
  }
}
