package com.gameio.gameresult.replay.memorymatch;

import java.util.List;

public record MemoryState(
        int rows,
        int columns,
        List<MemoryCellState> cells,
        List<Integer> selected,
        int matchedPairs,
        int moves,
        long score,
        boolean pendingMismatch,
        String status) {
    public MemoryState {
        cells = List.copyOf(cells);
        selected = List.copyOf(selected);
    }
}
