package com.gameio.multiplayer.engine.tank;

import java.util.UUID;

public record BulletView(UUID id, UUID ownerId, double x, double y) {
}
