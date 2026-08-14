package com.gameio.multiplayer.engine;

import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public interface AuthoritativeEngineFactory {
    String gameSlug();

    AuthoritativeEngine create(List<UUID> playerIds);

    AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper);
}
