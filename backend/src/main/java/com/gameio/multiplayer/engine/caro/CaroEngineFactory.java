package com.gameio.multiplayer.engine.caro;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class CaroEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "caro";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new CaroEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new CaroEngine(playerIds, objectMapper.treeToValue(checkpoint, CaroCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not restore Caro checkpoint", exception);
        }
    }
}
