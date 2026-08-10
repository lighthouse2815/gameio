package com.gameio.multiplayer.protocol;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record ClientEnvelope(
        String type,
        String requestId,
        String roomId,
        String gameSlug,
        JsonNode payload,
        Instant sentAt
) {
}
