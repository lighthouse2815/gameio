package com.gameio.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateRoomRequest(
        @NotNull UUID gameId,
        @Min(2) @Max(4) int maxPlayers,
        boolean privateRoom
) {
}
