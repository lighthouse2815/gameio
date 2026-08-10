package com.gameio.gameresult.multiplayer;

import com.gameio.gameresult.GameResultType;
import java.util.UUID;

public record AuthoritativePlayerOutcome(UUID userId, GameResultType result, long score) {
    public AuthoritativePlayerOutcome {
        if (userId == null || result == null || score < 0) {
            throw new IllegalArgumentException("Authoritative outcome is invalid");
        }
    }
}
