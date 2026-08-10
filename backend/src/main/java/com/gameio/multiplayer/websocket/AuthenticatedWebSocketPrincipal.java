package com.gameio.multiplayer.websocket;

import java.security.Principal;
import java.util.UUID;

public record AuthenticatedWebSocketPrincipal(UUID userId, String username) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
