package com.gameio.multiplayer.engine.tank;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TankCheckpoint(
        int version,
        long sequence,
        Instant lastTick,
        UUID winnerId,
        boolean draw,
        List<TankState> tanks,
        List<BulletState> bullets
) {
    public static final int CURRENT_VERSION = 1;

    public TankCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Tank Battle checkpoint version");
        }
        Objects.requireNonNull(tanks);
        Objects.requireNonNull(bullets);
        tanks = List.copyOf(tanks);
        bullets = List.copyOf(bullets);
    }

    public record TankState(
            UUID userId,
            double x,
            double y,
            double rotation,
            int hp,
            boolean alive,
            int kills,
            double dx,
            double dy,
            long lastInputSequence,
            Instant lastShotAt
    ) {
    }

    public record BulletState(
            UUID id,
            UUID ownerId,
            double x,
            double y,
            double dx,
            double dy
    ) {
    }
}
