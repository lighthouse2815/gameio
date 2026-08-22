package com.gameio.multiplayer.engine.sos;

import java.util.List;
import java.util.UUID;

public record SosSnapshot(
        long sequence,
        List<List<String>> board,
        UUID currentTurnPlayerId,
        List<SosPlayerSnapshot> players,
        UUID winnerId,
        boolean draw,
        Integer lastMoveRow,
        Integer lastMoveColumn,
        int lastMovePoints
) {
}
