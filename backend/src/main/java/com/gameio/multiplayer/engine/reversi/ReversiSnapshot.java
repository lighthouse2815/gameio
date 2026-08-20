package com.gameio.multiplayer.engine.reversi;

import java.util.List;
import java.util.UUID;

public record ReversiSnapshot(
        long sequence,
        List<List<String>> board,
        UUID currentTurnPlayerId,
        UUID winnerId,
        boolean draw,
        int blackCount,
        int whiteCount,
        List<ReversiMove> legalMoves,
        Integer lastMoveRow,
        Integer lastMoveColumn
) {
}
