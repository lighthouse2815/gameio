package com.gameio.multiplayer.engine.sos;

import java.util.UUID;

public record SosPlayerSnapshot(UUID userId, int score) {
}
