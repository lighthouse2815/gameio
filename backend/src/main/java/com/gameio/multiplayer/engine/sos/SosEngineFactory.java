package com.gameio.multiplayer.engine.sos;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SosEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "sos";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new SosEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new SosEngine(playerIds, objectMapper.treeToValue(checkpoint, SosCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not restore SOS checkpoint", exception);
        }
    }
}
