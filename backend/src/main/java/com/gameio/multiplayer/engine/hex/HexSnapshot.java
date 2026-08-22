package com.gameio.multiplayer.engine.hex;

import java.util.List;
import java.util.UUID;

public record HexSnapshot(
        long sequence,
        List<List<String>> board,
        UUID currentTurnPlayerId,
        UUID winnerId,
        Integer lastMoveRow,
        Integer lastMoveColumn
) {
}
