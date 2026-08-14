package com.gameio.gameresult.replay.minesweeper;

import com.gameio.gameresult.replay.SeededRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class MinesweeperEngine {
    public static final int ROWS = 9;
    public static final int COLUMNS = 9;
    public static final int MINE_COUNT = 10;

    private final long seed;
    private boolean[] mines;
    private final boolean[] revealed = new boolean[ROWS * COLUMNS];
    private int revealedCount;
    private long score;
    private int moves;
    private String status = "playing";

    public MinesweeperEngine(long seed) {
        this.seed = seed;
    }

    public MinesweeperState state() {
        List<MinesweeperCellState> cells = new ArrayList<>(revealed.length);
        for (int index = 0; index < revealed.length; index++) {
            int adjacent = 0;
            if (revealed[index]) {
                adjacent = mines != null && mines[index] ? -1 : adjacentMineCount(index);
            }
            cells.add(new MinesweeperCellState(revealed[index], false, adjacent));
        }
        return new MinesweeperState(ROWS, COLUMNS, MINE_COUNT, cells, revealedCount, score, moves, status);
    }

    public boolean terminal() {
        return !"playing".equals(status);
    }

    public boolean reveal(int index) {
        if (terminal() || index < 0 || index >= revealed.length || revealed[index]) {
            return false;
        }
        if (mines == null) {
            placeMines(index);
        }
        moves += 1;
        if (mines[index]) {
            revealed[index] = true;
            status = "lost";
            return true;
        }

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        boolean[] queued = new boolean[revealed.length];
        queue.add(index);
        queued[index] = true;
        while (!queue.isEmpty()) {
            int candidate = queue.removeFirst();
            if (revealed[candidate] || mines[candidate]) {
                continue;
            }
            revealed[candidate] = true;
            revealedCount += 1;
            if (adjacentMineCount(candidate) == 0) {
                for (int neighbor : neighbors(candidate)) {
                    if (!queued[neighbor] && !mines[neighbor]) {
                        queued[neighbor] = true;
                        queue.addLast(neighbor);
                    }
                }
            }
        }
        if (revealedCount == ROWS * COLUMNS - MINE_COUNT) {
            status = "won";
        }
        score = (long) revealedCount * 10 + ("won".equals(status) ? 500 : 0);
        return true;
    }

    private void placeMines(int firstIndex) {
        SeededRandom random = new SeededRandom(seed);
        List<Integer> candidates = new ArrayList<>(ROWS * COLUMNS - 1);
        for (int index = 0; index < ROWS * COLUMNS; index++) {
            if (index != firstIndex) {
                candidates.add(index);
            }
        }
        for (int index = candidates.size() - 1; index > 0; index--) {
            int swapIndex = random.nextIndex(index + 1);
            int value = candidates.get(index);
            candidates.set(index, candidates.get(swapIndex));
            candidates.set(swapIndex, value);
        }
        mines = new boolean[ROWS * COLUMNS];
        for (int index = 0; index < MINE_COUNT; index++) {
            mines[candidates.get(index)] = true;
        }
    }

    private List<Integer> neighbors(int index) {
        int row = index / COLUMNS;
        int column = index % COLUMNS;
        List<Integer> neighbors = new ArrayList<>(8);
        for (int rowDelta = -1; rowDelta <= 1; rowDelta++) {
            for (int columnDelta = -1; columnDelta <= 1; columnDelta++) {
                if (rowDelta == 0 && columnDelta == 0) {
                    continue;
                }
                int candidateRow = row + rowDelta;
                int candidateColumn = column + columnDelta;
                if (candidateRow >= 0 && candidateRow < ROWS
                        && candidateColumn >= 0 && candidateColumn < COLUMNS) {
                    neighbors.add(candidateRow * COLUMNS + candidateColumn);
                }
            }
        }
        return neighbors;
    }

    private int adjacentMineCount(int index) {
        if (mines == null) {
            return 0;
        }
        int count = 0;
        for (int neighbor : neighbors(index)) {
            if (mines[neighbor]) {
                count += 1;
            }
        }
        return count;
    }
}
