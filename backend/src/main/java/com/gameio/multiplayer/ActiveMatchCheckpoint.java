package com.gameio.multiplayer;

import com.gameio.room.RoomState;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record ActiveMatchCheckpoint(
        UUID matchId,
        RoomState room,
        Instant startedAt,
        Instant lastActivityAt,
        Map<UUID, Instant> disconnectedAt,
        JsonNode engineState,
        Instant savedAt
) {
    public ActiveMatchCheckpoint {
        Objects.requireNonNull(matchId);
        Objects.requireNonNull(room);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(lastActivityAt);
        Objects.requireNonNull(engineState);
        Objects.requireNonNull(savedAt);
        disconnectedAt = Map.copyOf(disconnectedAt);
    }
}
