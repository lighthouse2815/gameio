package com.gameio.multiplayer.engine.connectfour;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConnectFourEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "connect-four";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new ConnectFourEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new ConnectFourEngine(playerIds,
                    objectMapper.treeToValue(checkpoint, ConnectFourCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not restore Connect Four checkpoint", exception);
        }
    }
}
