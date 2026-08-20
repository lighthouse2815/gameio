package com.gameio.multiplayer.engine.rps;

import java.util.List;
import java.util.UUID;

public record RpsSnapshot(
        long sequence,
        int round,
        int targetWins,
        List<RpsPlayerSnapshot> players,
        RpsRoundSnapshot lastRound,
        UUID winnerId,
        boolean draw
) {
}
