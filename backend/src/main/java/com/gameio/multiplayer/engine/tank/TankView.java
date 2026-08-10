package com.gameio.multiplayer.engine.tank;

import java.util.UUID;

public record TankView(
        UUID userId,
        double x,
        double y,
        double rotation,
        int hp,
        boolean alive,
        int kills,
        long lastInputSequence
) {
}
