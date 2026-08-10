package com.gameio.multiplayer.engine.tictactoe;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

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
}
