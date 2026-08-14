package com.gameio.multiplayer.engine;

import com.gameio.common.error.InvalidGameActionException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class EngineRegistry {
    private final Map<String, AuthoritativeEngineFactory> factories;
    private final ObjectMapper objectMapper;

    public EngineRegistry(List<AuthoritativeEngineFactory> engineFactories, ObjectMapper objectMapper) {
        Map<String, AuthoritativeEngineFactory> indexed = new HashMap<>();
        for (AuthoritativeEngineFactory factory : engineFactories) {
            if (indexed.put(factory.gameSlug(), factory) != null) {
                throw new IllegalStateException("Duplicate authoritative engine: " + factory.gameSlug());
            }
        }
        this.factories = Map.copyOf(indexed);
        this.objectMapper = objectMapper;
    }

    public AuthoritativeEngine create(String gameSlug, List<UUID> players) {
        AuthoritativeEngineFactory factory = factories.get(gameSlug);
        if (factory == null) {
            throw new InvalidGameActionException("No authoritative realtime engine is registered for this game");
        }
        return factory.create(players);
    }

    public AuthoritativeEngine restore(String gameSlug, List<UUID> players, JsonNode checkpoint) {
        AuthoritativeEngineFactory factory = factories.get(gameSlug);
        if (factory == null) {
            throw new InvalidGameActionException("No authoritative realtime engine is registered for this game");
        }
        if (checkpoint == null || checkpoint.isNull()) {
            throw new InvalidGameActionException("Authoritative engine checkpoint is missing");
        }
        return factory.restore(players, checkpoint, objectMapper);
    }

    public JsonNode checkpoint(AuthoritativeEngine engine) {
        return objectMapper.valueToTree(engine.checkpoint());
    }
}
