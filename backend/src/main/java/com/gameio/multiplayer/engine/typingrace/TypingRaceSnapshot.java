package com.gameio.multiplayer.engine.typingrace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TypingRaceSnapshot(
        long sequence,
        String passageId,
        String passage,
        Instant startsAt,
        Instant deadline,
        List<TypingRacePlayerSnapshot> players,
        UUID winnerId,
        boolean draw,
        boolean terminal
) {
    public TypingRaceSnapshot {
        players = List.copyOf(players);
    }
}
