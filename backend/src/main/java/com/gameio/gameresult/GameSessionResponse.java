package com.gameio.gameresult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GameSessionResponse(
        UUID sessionId,
        String gameSlug,
        long seed,
        Object initialState,
        Instant expiresAt,
        LocalDate challengeDate
) {
}
