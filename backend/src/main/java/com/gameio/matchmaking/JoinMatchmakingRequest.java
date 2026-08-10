package com.gameio.matchmaking;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record JoinMatchmakingRequest(@NotNull UUID gameId) {
}
