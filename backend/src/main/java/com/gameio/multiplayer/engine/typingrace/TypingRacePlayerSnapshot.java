package com.gameio.multiplayer.engine.typingrace;

import java.time.Instant;
import java.util.UUID;

public record TypingRacePlayerSnapshot(
        UUID userId,
        int progress,
        int correctCharacters,
        int errors,
        int combo,
        int bestCombo,
        long lastInputSequence,
        int wpm,
        int accuracyPercent,
        boolean finished,
        Instant finishedAt
) {
}
