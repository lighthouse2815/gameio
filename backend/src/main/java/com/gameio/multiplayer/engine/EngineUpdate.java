package com.gameio.multiplayer.engine;

import java.util.List;

public record EngineUpdate(
        boolean changed,
        Object snapshot,
        boolean terminal,
        List<EngineOutcome> outcomes
) {
    public EngineUpdate {
        outcomes = List.copyOf(outcomes);
    }

    public static EngineUpdate state(Object snapshot) {
        return new EngineUpdate(true, snapshot, false, List.of());
    }
}
