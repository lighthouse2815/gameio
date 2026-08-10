package com.gameio.multiplayer.engine.caro;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CaroEngineFactory implements AuthoritativeEngineFactory {
    @Override
    public String gameSlug() {
        return "caro";
    }

    @Override
    public AuthoritativeEngine create(List<UUID> playerIds) {
        return new CaroEngine(playerIds);
    }
}
