package com.gameio.multiplayer.engine.tictactoe;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TicTacToeEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "tic-tac-toe";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new TicTacToeEngine(playerIds);
    }

    @Override
    public AuthoritativeEngine restore(List<UUID> playerIds, JsonNode checkpoint, ObjectMapper objectMapper) {
        try {
            return new TicTacToeEngine(playerIds, objectMapper.treeToValue(checkpoint, TicTacToeCheckpoint.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not restore Tic Tac Toe checkpoint", exception);
        }
    }
}
