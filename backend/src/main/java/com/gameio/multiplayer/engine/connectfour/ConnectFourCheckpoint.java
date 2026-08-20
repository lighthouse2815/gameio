package com.gameio.multiplayer.engine.connectfour;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ConnectFourCheckpoint(
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

    public ConnectFourCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Connect Four checkpoint version");
        }
        Objects.requireNonNull(board);
        board = board.stream().map(List::copyOf).toList();
    }
}
