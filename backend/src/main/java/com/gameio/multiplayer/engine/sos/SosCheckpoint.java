package com.gameio.multiplayer.engine.sos;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SosCheckpoint(
        int version,
        long sequence,
        List<List<Integer>> board,
        int currentPlayer,
        int moves,
        List<Integer> scores,
        UUID winnerId,
        boolean draw,
        List<SosMoveCheckpoint> moveHistory,
        Integer lastMoveRow,
        Integer lastMoveColumn,
        int lastMovePoints
) {
    public static final int CURRENT_VERSION = 1;

    public SosCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported SOS checkpoint version");
        }
        Objects.requireNonNull(board);
        Objects.requireNonNull(scores);
        Objects.requireNonNull(moveHistory);
        board = board.stream().map(List::copyOf).toList();
        scores = List.copyOf(scores);
        moveHistory = List.copyOf(moveHistory);
    }
}
