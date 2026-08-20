package com.gameio.multiplayer.engine.connectfour;

import java.util.List;
import java.util.UUID;

public record ConnectFourSnapshot(
        long sequence,
        List<List<String>> board,
        UUID currentTurnPlayerId,
        UUID winnerId,
        boolean draw,
        Integer lastMoveRow,
        Integer lastMoveColumn
) {
}
