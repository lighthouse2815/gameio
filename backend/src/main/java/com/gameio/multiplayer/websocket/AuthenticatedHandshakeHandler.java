package com.gameio.multiplayer.websocket;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class AuthenticatedHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(
            ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        UUID userId = (UUID) attributes.get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
        String username = (String) attributes.get(JwtHandshakeInterceptor.USERNAME_ATTRIBUTE);
        return new AuthenticatedWebSocketPrincipal(userId, username);
    }
}
