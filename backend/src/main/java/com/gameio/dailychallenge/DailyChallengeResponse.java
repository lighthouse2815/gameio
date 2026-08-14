package com.gameio.dailychallenge;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyChallengeResponse(
        LocalDate date,
        UUID gameId,
        String gameSlug,
        String gameName,
        String gameDescription,
        Instant startsAt,
        Instant endsAt
) {
}
