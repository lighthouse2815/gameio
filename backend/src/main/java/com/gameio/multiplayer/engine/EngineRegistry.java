package com.gameio.multiplayer.engine;

import com.gameio.common.error.InvalidGameActionException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EngineRegistry {
    private final Map<String, AuthoritativeEngineFactory> factories;

    public EngineRegistry(List<AuthoritativeEngineFactory> engineFactories) {
        Map<String, AuthoritativeEngineFactory> indexed = new HashMap<>();
        for (AuthoritativeEngineFactory factory : engineFactories) {
            if (indexed.put(factory.gameSlug(), factory) != null) {
                throw new IllegalStateException("Duplicate authoritative engine: " + factory.gameSlug());
            }
        }
        this.factories = Map.copyOf(indexed);
    }

    public AuthoritativeEngine create(String gameSlug, List<UUID> players) {
        AuthoritativeEngineFactory factory = factories.get(gameSlug);
        if (factory == null) {
            throw new InvalidGameActionException("No authoritative realtime engine is registered for this game");
        }
        return factory.create(players);
    }
}
