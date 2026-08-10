package com.gameio.multiplayer.engine.tank;

import java.util.List;
import java.util.UUID;

public record TankSnapshot(
        long sequence,
        double width,
        double height,
        List<TankView> tanks,
        List<BulletView> bullets,
        UUID winnerId,
        boolean draw
) {
}
