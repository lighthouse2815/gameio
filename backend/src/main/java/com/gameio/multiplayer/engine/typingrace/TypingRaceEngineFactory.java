package com.gameio.multiplayer.engine.typingrace;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TypingRaceEngineFactory implements AuthoritativeEngineFactory {
    private final Clock clock;
    private final TypingPassageCatalog passages;

    public TypingRaceEngineFactory(Clock clock, TypingPassageCatalog passages) {
        this.clock = clock;
        this.passages = passages;
    }

    @Override
    public String gameSlug() {
        return "typing-race";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new TypingRaceEngine(playerIds, passages.next(), Instant.now(clock));
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new TypingRaceEngine(playerIds,
                    objectMapper.treeToValue(checkpoint, TypingRaceCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not restore Typing Race checkpoint", exception);
        }
    }
}
