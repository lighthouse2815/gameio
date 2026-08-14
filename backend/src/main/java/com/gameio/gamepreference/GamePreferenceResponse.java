package com.gameio.gamepreference;

import java.time.Instant;
import java.util.UUID;

public record GamePreferenceResponse(
        UUID gameId,
        String gameSlug,
        boolean favorite,
        Instant lastPlayedAt,
        Instant updatedAt
) {
    static GamePreferenceResponse from(GamePreference preference) {
        return new GamePreferenceResponse(preference.gameId(), preference.gameSlug(), preference.favorite(),
                preference.lastPlayedAt(), preference.updatedAt());
    }
}
