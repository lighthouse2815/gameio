package com.gameio.multiplayer.websocket;

import com.gameio.common.web.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {
    private final GameWebSocketHandler gameWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final AuthenticatedHandshakeHandler handshakeHandler;
    private final CorsProperties corsProperties;

    public WebSocketConfig(
            GameWebSocketHandler gameWebSocketHandler,
            JwtHandshakeInterceptor jwtHandshakeInterceptor,
            AuthenticatedHandshakeHandler handshakeHandler,
            CorsProperties corsProperties) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Bean
    @Profile("!test")
    ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(65_536);
        // Keep the servlet container's binary frame buffer bounded too. A zero-sized
        // buffer prevents some Tomcat clients from dispatching subsequent text frames.
        container.setMaxBinaryMessageBufferSize(65_536);
        container.setMaxSessionIdleTimeout(0L);
        return container;
    }
}
