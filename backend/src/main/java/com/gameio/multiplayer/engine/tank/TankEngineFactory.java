package com.gameio.multiplayer.engine.tank;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TankEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "tank-battle";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new TankEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new TankEngine(playerIds, objectMapper.treeToValue(checkpoint, TankCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not restore Tank Battle checkpoint", exception);
        }
    }
}
