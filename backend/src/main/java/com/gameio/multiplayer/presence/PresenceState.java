package com.gameio.multiplayer.presence;

import java.time.Instant;
import java.util.UUID;

public record PresenceState(
        UUID userId,
        boolean online,
        UUID roomId,
        String gameSlug,
        String gameName,
        Instant lastSeenAt
) {
    public static PresenceState offline(UUID userId, Instant now) {
        return new PresenceState(userId, false, null, null, null, now);
    }
}
