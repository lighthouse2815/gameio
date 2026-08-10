package com.gameio.multiplayer.engine;

public record GameInput(String action, Integer row, Integer column, Long sequence) {
    public GameInput {
        if (action == null || action.isBlank() || action.length() > 32) {
            throw new IllegalArgumentException("Game action is required");
        }
    }
}
