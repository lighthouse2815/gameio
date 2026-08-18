package com.gameio.multiplayer.engine;

public record GameInput(String action, Integer row, Integer column, Long sequence, String character) {
    public GameInput {
        if (action == null || action.isBlank() || action.length() > 32) {
            throw new IllegalArgumentException("Game action is required");
        }
    }

    public GameInput(String action, Integer row, Integer column, Long sequence) {
        this(action, row, column, sequence, null);
    }
}
