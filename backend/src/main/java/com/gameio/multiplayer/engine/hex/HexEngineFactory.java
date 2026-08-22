package com.gameio.multiplayer.engine.hex;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class HexEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "hex";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new HexEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new HexEngine(playerIds, objectMapper.treeToValue(checkpoint, HexCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not restore Hex checkpoint", exception);
        }
    }
}
