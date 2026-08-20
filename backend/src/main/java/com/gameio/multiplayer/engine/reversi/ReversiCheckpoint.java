package com.gameio.multiplayer.engine.reversi;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReversiCheckpoint(
        int version,
        long sequence,
        List<List<Integer>> board,
        int currentPlayer,
        int moves,
        UUID winnerId,
        boolean draw,
        Integer lastMoveRow,
        Integer lastMoveColumn
) {
    public static final int CURRENT_VERSION = 1;

    public ReversiCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Reversi checkpoint version");
        }
        Objects.requireNonNull(board);
        board = board.stream().map(List::copyOf).toList();
    }
}
