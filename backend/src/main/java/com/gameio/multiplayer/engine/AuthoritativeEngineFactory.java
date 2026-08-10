package com.gameio.multiplayer.engine;

import java.util.List;
import java.util.UUID;

public interface AuthoritativeEngineFactory {
    String gameSlug();

    AuthoritativeEngine create(List<UUID> playerIds);
}
