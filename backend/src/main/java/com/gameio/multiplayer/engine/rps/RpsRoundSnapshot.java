package com.gameio.multiplayer.engine.rps;

import java.util.UUID;

public record RpsRoundSnapshot(
        int round,
        String firstChoice,
        String secondChoice,
        UUID winnerId,
        boolean draw
) {
}
