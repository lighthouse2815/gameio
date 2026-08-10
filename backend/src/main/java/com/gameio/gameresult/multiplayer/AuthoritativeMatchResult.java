package com.gameio.gameresult.multiplayer;

import java.util.List;
import java.util.UUID;

public record AuthoritativeMatchResult(
        UUID matchId,
        UUID gameId,
        int durationSeconds,
        List<AuthoritativePlayerOutcome> outcomes
) {
    public AuthoritativeMatchResult {
        outcomes = List.copyOf(outcomes);
        if (matchId == null || gameId == null || durationSeconds < 1 || outcomes.size() < 2 || outcomes.size() > 4) {
            throw new IllegalArgumentException("Authoritative match result is invalid");
        }
        if (outcomes.stream().map(AuthoritativePlayerOutcome::userId).distinct().count() != outcomes.size()) {
            throw new IllegalArgumentException("A player may only have one authoritative outcome");
        }
    }
}
