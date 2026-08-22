package com.gameio.multiplayer.engine.ultimatettt;

import java.util.List;
import java.util.UUID;

public record UltimateTttSnapshot(
        long sequence,
        List<List<String>> board,
        List<List<String>> subBoards,
        Integer forcedBoardRow,
        Integer forcedBoardColumn,
        List<UltimateTttMove> legalMoves,
        UUID currentTurnPlayerId,
        UUID winnerId,
        boolean draw,
        Integer lastMoveRow,
        Integer lastMoveColumn
) {
    public UltimateTttSnapshot {
        board = board.stream().map(List::copyOf).toList();
        subBoards = subBoards.stream().map(List::copyOf).toList();
        legalMoves = List.copyOf(legalMoves);
    }
}
