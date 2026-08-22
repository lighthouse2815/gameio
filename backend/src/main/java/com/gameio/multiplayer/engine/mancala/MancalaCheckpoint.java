package com.gameio.multiplayer.engine.mancala;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MancalaCheckpoint(
        int version,
        long sequence,
        List<Integer> pits,
        int currentPlayer,
        Integer lastPit,
        UUID winnerId,
        boolean draw
) {
    public static final int CURRENT_VERSION = 1;

    public MancalaCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Mancala checkpoint version");
        }
        Objects.requireNonNull(pits);
        pits = List.copyOf(pits);
    }
}
