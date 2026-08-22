package com.gameio.multiplayer.engine.mancala;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class MancalaEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "mancala";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new MancalaEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new MancalaEngine(playerIds,
                    objectMapper.treeToValue(checkpoint, MancalaCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Mancala checkpoint is malformed", exception);
        }
    }
}
