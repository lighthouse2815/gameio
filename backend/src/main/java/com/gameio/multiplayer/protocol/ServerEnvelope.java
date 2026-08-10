package com.gameio.multiplayer.protocol;

import java.time.Instant;
import java.util.UUID;

public record ServerEnvelope(
        String type,
        String requestId,
        UUID roomId,
        Object payload,
        Instant timestamp
) {
}
