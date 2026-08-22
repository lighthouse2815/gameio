package com.gameio.multiplayer.engine.ultimatettt;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record UltimateTttCheckpoint(
        int version,
        long sequence,
        List<List<Integer>> board,
        int currentPlayer,
        int moves,
        Integer requiredSubBoard,
        UUID winnerId,
        boolean draw,
        Integer lastMoveRow,
        Integer lastMoveColumn
) {
    public static final int CURRENT_VERSION = 1;

    public UltimateTttCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Ultimate Tic Tac Toe checkpoint version");
        }
        Objects.requireNonNull(board);
        board = board.stream().map(List::copyOf).toList();
    }
}
