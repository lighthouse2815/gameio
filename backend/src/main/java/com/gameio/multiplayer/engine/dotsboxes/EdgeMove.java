package com.gameio.multiplayer.engine.dotsboxes;

public record EdgeMove(String orientation, int row, int column) {
    public EdgeMove {
        if (!"H".equals(orientation) && !"V".equals(orientation)) {
            throw new IllegalArgumentException("Dots and Boxes edge orientation must be H or V");
        }
    }
}
