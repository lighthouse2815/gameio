package com.gameio.multiplayer.websocket;

import com.gameio.user.UserAccount;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USER_ID_ATTRIBUTE = "gameio.userId";
    public static final String USERNAME_ATTRIBUTE = "gameio.username";
    public static final String TOKEN_EXPIRES_AT_ATTRIBUTE = "gameio.tokenExpiresAt";
    public static final String APPLICATION_PROTOCOL = "gameio.v1";
    public static final String JWT_PROTOCOL_PREFIX = "gameio.jwt.";
    private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";

    private final JwtDecoder jwtDecoder;
    private final UserRepository users;
    private final RealtimeRateLimiter rateLimiter;
    private final Clock clock;

    public JwtHandshakeInterceptor(
            JwtDecoder jwtDecoder, UserRepository users, RealtimeRateLimiter rateLimiter, Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        try {
            rateLimiter.checkHandshake(clientAddress(request));
        } catch (RealtimeRateLimitException exception) {
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return false;
        }
        List<String> protocols = request.getHeaders().getOrEmpty(PROTOCOL_HEADER).stream()
                .flatMap(header -> Arrays.stream(header.split(",")))
                .map(String::trim)
                .filter(protocol -> !protocol.isEmpty())
                .toList();
        List<String> tokenProtocols = protocols.stream()
                .filter(protocol -> protocol.startsWith(JWT_PROTOCOL_PREFIX))
                .toList();
        if (!protocols.contains(APPLICATION_PROTOCOL) || tokenProtocols.size() != 1) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String accessToken = tokenProtocols.getFirst().substring(JWT_PROTOCOL_PREFIX.length());
        if (accessToken.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(accessToken);
            UUID userId = UUID.fromString(jwt.getSubject());
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null || !expiresAt.isAfter(Instant.now(clock))) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            UserAccount user = users.findById(userId).orElse(null);
            if (user == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            try {
                rateLimiter.checkHandshake(userId);
            } catch (RealtimeRateLimitException exception) {
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return false;
            }
            attributes.put(USER_ID_ATTRIBUTE, userId);
            attributes.put(USERNAME_ATTRIBUTE, user.getUsername());
            attributes.put(TOKEN_EXPIRES_AT_ATTRIBUTE, expiresAt);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }

    private String clientAddress(ServerHttpRequest request) {
        java.net.InetSocketAddress address = request.getRemoteAddress();
        if (address == null) return "unknown";
        return address.getAddress() == null ? address.getHostString() : address.getAddress().getHostAddress();
    }
}
