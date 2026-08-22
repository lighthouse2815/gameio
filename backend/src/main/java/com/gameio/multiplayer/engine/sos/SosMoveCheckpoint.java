package com.gameio.multiplayer.engine.sos;

public record SosMoveCheckpoint(
        int playerIndex,
        int marker,
        int row,
        int column,
        int points
) {
}
