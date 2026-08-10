package com.gameio.multiplayer.engine.caro;

import java.util.List;
import java.util.UUID;

public record CaroSnapshot(
        long sequence,
        int boardSize,
        List<List<String>> board,
        UUID currentTurnPlayerId,
        UUID winnerId,
        boolean draw
) {
}
