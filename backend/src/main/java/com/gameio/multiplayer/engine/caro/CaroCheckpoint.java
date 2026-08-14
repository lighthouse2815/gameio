package com.gameio.multiplayer.engine.caro;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CaroCheckpoint(
        int version,
        long sequence,
        List<List<Integer>> board,
        int currentPlayer,
        int moves,
        UUID winnerId,
        boolean draw
) {
    public static final int CURRENT_VERSION = 1;

    public CaroCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Caro checkpoint version");
        }
        Objects.requireNonNull(board);
        board = board.stream().map(List::copyOf).toList();
    }
}
