package com.gameio.multiplayer.engine.typingrace;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TypingRaceCheckpoint(
        int version,
        long sequence,
        String passageId,
        String passage,
        Instant startsAt,
        Instant deadline,
        Instant endedAt,
        UUID winnerId,
        boolean draw,
        List<PlayerState> players
) {
    public static final int CURRENT_VERSION = 1;

    public TypingRaceCheckpoint {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Typing Race checkpoint version");
        }
        Objects.requireNonNull(players);
        players = List.copyOf(players);
    }

    public record PlayerState(
            UUID userId,
            int progress,
            int correctCharacters,
            int errors,
            int combo,
            int bestCombo,
            long lastInputSequence,
            Instant lastInputAt,
            Instant finishedAt
    ) {
    }
}
