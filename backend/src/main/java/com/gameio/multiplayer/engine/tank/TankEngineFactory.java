package com.gameio.multiplayer.engine.tank;

import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.AuthoritativeEngineFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

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
}
