package com.gameio.multiplayer.engine.reversi;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ReversiEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "reversi";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new ReversiEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new ReversiEngine(playerIds, objectMapper.treeToValue(checkpoint, ReversiCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not restore Reversi checkpoint", exception);
        }
    }
}
