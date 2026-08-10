package com.gameio.multiplayer.engine.tictactoe;

import java.util.List;
import java.util.UUID;

public record TicTacToeSnapshot(
        long sequence,
        List<List<String>> board,
        UUID currentTurnPlayerId,
        UUID winnerId,
        boolean draw
) {
}
