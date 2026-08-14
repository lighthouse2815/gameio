package com.gameio.multiplayer.engine;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuthoritativeEngine {
    Object snapshot();

    Object checkpoint();

    EngineUpdate input(UUID userId, GameInput input, Instant now);

    default EngineUpdate tick(Instant now) {
        return new EngineUpdate(false, snapshot(), false, java.util.List.of());
    }

    default boolean requiresServerTick() {
        return false;
    }

    default boolean terminal() {
        return false;
    }

    default List<EngineOutcome> outcomes() {
        return List.of();
    }
}
