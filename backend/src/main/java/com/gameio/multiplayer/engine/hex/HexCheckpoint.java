package com.gameio.multiplayer.engine.hex;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record HexCheckpoint(
        int version,
        long sequence,
        List<List<Integer>> board,
        int currentPlayer,
        int moves,
        UUID winnerId,
        Integer lastMoveRow,
        Integer lastMoveColumn
) {
    public static final int CURRENT_VERSION = 1;

    public HexCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Hex checkpoint version");
        }
        Objects.requireNonNull(board);
        board = board.stream().map(List::copyOf).toList();
    }
}
