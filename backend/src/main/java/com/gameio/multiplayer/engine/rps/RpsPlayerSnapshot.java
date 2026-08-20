package com.gameio.multiplayer.engine.rps;

import java.util.UUID;

public record RpsPlayerSnapshot(UUID userId, int wins, boolean submitted) {
}
