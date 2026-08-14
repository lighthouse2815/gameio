package com.gameio.gameresult.replay.minesweeper;

import java.util.List;

public record MinesweeperState(
        int rows,
        int columns,
        int mineCount,
        List<MinesweeperCellState> cells,
        int revealedCount,
        long score,
        int moves,
        String status) {
    public MinesweeperState {
        cells = List.copyOf(cells);
    }
}
