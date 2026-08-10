package com.gameio.gameresult.replay.game2048;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Game2048Engine {
    private static final int SIZE = 4;
    private final int[][] board = new int[SIZE][SIZE];
    private final DeterministicRandom random;
    private long score;

    public Game2048Engine(long seed) {
        this.random = new DeterministicRandom(seed);
        addRandomTile();
        addRandomTile();
    }

    public boolean move(MoveDirection direction) {
        boolean changed = false;
        for (int index = 0; index < SIZE; index++) {
            int[] original = readLine(index, direction);
            int[] merged = merge(original);
            if (!Arrays.equals(original, merged)) {
                changed = true;
                writeLine(index, direction, merged);
            }
        }
        if (changed) {
            addRandomTile();
        }
        return changed;
    }

    public Game2048State state() {
        List<List<Integer>> rows = new ArrayList<>(SIZE);
        int highest = 0;
        for (int[] row : board) {
            List<Integer> values = new ArrayList<>(SIZE);
            for (int value : row) {
                values.add(value);
                highest = Math.max(highest, value);
            }
            rows.add(List.copyOf(values));
        }
        return new Game2048State(List.copyOf(rows), score, isGameOver(), highest);
    }

    private int[] merge(int[] line) {
        int[] compact = Arrays.stream(line).filter(value -> value != 0).toArray();
        int[] merged = new int[SIZE];
        int target = 0;
        for (int index = 0; index < compact.length; index++) {
            int value = compact[index];
            if (index + 1 < compact.length && compact[index + 1] == value) {
                value = Math.multiplyExact(value, 2);
                score = Math.addExact(score, value);
                index++;
            }
            merged[target++] = value;
        }
        return merged;
    }

    private int[] readLine(int index, MoveDirection direction) {
        int[] line = new int[SIZE];
        for (int offset = 0; offset < SIZE; offset++) {
            line[offset] = switch (direction) {
                case LEFT -> board[index][offset];
                case RIGHT -> board[index][SIZE - 1 - offset];
                case UP -> board[offset][index];
                case DOWN -> board[SIZE - 1 - offset][index];
            };
        }
        return line;
    }

    private void writeLine(int index, MoveDirection direction, int[] line) {
        for (int offset = 0; offset < SIZE; offset++) {
            switch (direction) {
                case LEFT -> board[index][offset] = line[offset];
                case RIGHT -> board[index][SIZE - 1 - offset] = line[offset];
                case UP -> board[offset][index] = line[offset];
                case DOWN -> board[SIZE - 1 - offset][index] = line[offset];
            }
        }
    }

    private void addRandomTile() {
        List<Integer> emptyCells = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (board[row][column] == 0) {
                    emptyCells.add(row * SIZE + column);
                }
            }
        }
        if (emptyCells.isEmpty()) {
            return;
        }
        int cell = emptyCells.get(random.nextInt(emptyCells.size()));
        board[cell / SIZE][cell % SIZE] = random.nextInt(10) < 9 ? 2 : 4;
    }

    private boolean isGameOver() {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int value = board[row][column];
                if (value == 0) {
                    return false;
                }
                if (row + 1 < SIZE && board[row + 1][column] == value) {
                    return false;
                }
                if (column + 1 < SIZE && board[row][column + 1] == value) {
                    return false;
                }
            }
        }
        return true;
    }
}
