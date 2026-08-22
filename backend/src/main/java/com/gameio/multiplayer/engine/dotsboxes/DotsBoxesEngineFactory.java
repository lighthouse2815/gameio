package com.gameio.multiplayer.engine.dotsboxes;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DotsBoxesEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "dots-and-boxes";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new DotsBoxesEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new DotsBoxesEngine(playerIds,
                    objectMapper.treeToValue(checkpoint, DotsBoxesCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Dots and Boxes checkpoint is malformed", exception);
        }
    }
}
