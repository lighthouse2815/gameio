package com.gameio.multiplayer.engine.ultimatettt;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class UltimateTttEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "ultimate-tic-tac-toe";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new UltimateTttEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new UltimateTttEngine(playerIds,
                    objectMapper.treeToValue(checkpoint, UltimateTttCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Ultimate Tic Tac Toe checkpoint is malformed", exception);
        }
    }
}
