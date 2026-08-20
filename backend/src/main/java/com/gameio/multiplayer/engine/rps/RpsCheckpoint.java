package com.gameio.multiplayer.engine.rps;

import java.util.List;
import java.util.UUID;

public record RpsCheckpoint(
        int version,
        long sequence,
        int round,
        List<Integer> scores,
        List<Integer> choices,
        Integer lastRoundNumber,
        Integer lastFirstChoice,
        Integer lastSecondChoice,
        UUID lastRoundWinnerId,
        boolean lastRoundDraw,
        UUID winnerId
) {
    public static final int CURRENT_VERSION = 1;

    public RpsCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Rock Paper Scissors checkpoint version");
        }
        scores = List.copyOf(scores);
        choices = List.copyOf(choices);
    }
}
