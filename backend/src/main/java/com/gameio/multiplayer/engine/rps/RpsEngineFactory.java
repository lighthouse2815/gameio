package com.gameio.multiplayer.engine.rps;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RpsEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "rock-paper-scissors";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new RpsEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new RpsEngine(playerIds, objectMapper.treeToValue(checkpoint, RpsCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not restore Rock Paper Scissors checkpoint", exception);
        }
    }
}
