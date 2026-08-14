package com.gameio.gameresult.replay.memorymatch;

import com.gameio.gameresult.replay.SeededRandom;
import java.util.ArrayList;
import java.util.List;

public final class MemoryMatchEngine {
    public static final int ROWS = 4;
    public static final int COLUMNS = 4;
    public static final int PAIR_COUNT = 8;

    private final int[] deck = new int[PAIR_COUNT * 2];
    private final boolean[] revealed = new boolean[deck.length];
    private final boolean[] matched = new boolean[deck.length];
    private final List<Integer> selected = new ArrayList<>(2);
    private int matchedPairs;
    private int moves;
    private long score;
    private boolean pendingMismatch;
    private String status = "playing";

    public MemoryMatchEngine(long seed) {
        for (int value = 0; value < PAIR_COUNT; value++) {
            deck[value * 2] = value;
            deck[value * 2 + 1] = value;
        }
        SeededRandom random = new SeededRandom(seed);
        for (int index = deck.length - 1; index > 0; index--) {
            int swapIndex = random.nextIndex(index + 1);
            int value = deck[index];
            deck[index] = deck[swapIndex];
            deck[swapIndex] = value;
        }
    }

    public MemoryState state() {
        List<MemoryCellState> cells = new ArrayList<>(deck.length);
        for (int index = 0; index < deck.length; index++) {
            cells.add(new MemoryCellState(revealed[index] || matched[index] ? deck[index] : null,
                    revealed[index], matched[index]));
        }
        return new MemoryState(ROWS, COLUMNS, cells, selected, matchedPairs, moves, score,
                pendingMismatch, status);
    }

    public boolean terminal() {
        return "won".equals(status);
    }

    public void clearMismatch() {
        if (!pendingMismatch) {
            return;
        }
        for (int index : selected) {
            if (!matched[index]) {
                revealed[index] = false;
            }
        }
        selected.clear();
        pendingMismatch = false;
    }

    public boolean select(int index) {
        if (pendingMismatch) {
            clearMismatch();
        }
        if (terminal() || index < 0 || index >= deck.length || revealed[index] || matched[index]) {
            return false;
        }
        revealed[index] = true;
        selected.add(index);
        if (selected.size() == 2) {
            moves += 1;
            int first = selected.get(0);
            int second = selected.get(1);
            if (deck[first] == deck[second]) {
                matched[first] = true;
                matched[second] = true;
                matchedPairs += 1;
                selected.clear();
            } else {
                pendingMismatch = true;
            }
        }
        if (matchedPairs == PAIR_COUNT) {
            status = "won";
        }
        score = (long) matchedPairs * 100
                + (terminal() ? Math.max(0, 500 - moves * 10) : 0);
        return true;
    }
}
