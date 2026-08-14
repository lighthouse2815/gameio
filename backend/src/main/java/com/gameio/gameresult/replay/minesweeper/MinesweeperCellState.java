package com.gameio.gameresult.replay.minesweeper;

public record MinesweeperCellState(boolean revealed, boolean flagged, int adjacent) {
}
