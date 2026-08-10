package com.gameio.multiplayer.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.common.web.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

class WebSocketConfigTest {
    @Test
    void registersOnlyConfiguredExactOrigins() {
        GameWebSocketHandler handler = mock(GameWebSocketHandler.class);
        JwtHandshakeInterceptor interceptor = mock(JwtHandshakeInterceptor.class);
        AuthenticatedHandshakeHandler handshakeHandler = mock(AuthenticatedHandshakeHandler.class);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/ws")).thenReturn(registration);
        when(registration.addInterceptors(interceptor)).thenReturn(registration);
        when(registration.setHandshakeHandler(handshakeHandler)).thenReturn(registration);
        when(registration.setAllowedOrigins("https://gameio.example", "http://localhost:3000"))
                .thenReturn(registration);
        WebSocketConfig config = new WebSocketConfig(handler, interceptor, handshakeHandler,
                new CorsProperties(List.of("https://gameio.example", "http://localhost:3000")));

        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOrigins("https://gameio.example", "http://localhost:3000");
    }
}
