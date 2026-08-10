package com.gameio.multiplayer.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.user.UserAccount;
import com.gameio.user.UserRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.WebSocketHandler;

class JwtHandshakeInterceptorTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void authenticatesJwtSubprotocolAndUsesPersistedIdentityAsPrincipalAttributes() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        UserRepository users = mock(UserRepository.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        UUID userId = UUID.randomUUID();
        UserAccount user = UserAccount.create("SocketPlayer", "socket@example.com", "hash", Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        Jwt jwt = Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(300))
                .build();
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "gameio.v1, gameio.jwt.signed-token");
        when(request.getHeaders()).thenReturn(headers);
        when(decoder.decode("signed-token")).thenReturn(jwt);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = new JwtHandshakeInterceptor(decoder, users, mock(RealtimeRateLimiter.class), CLOCK)
                .beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes)
                .containsEntry(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE, userId)
                .containsEntry(JwtHandshakeInterceptor.USERNAME_ATTRIBUTE, "SocketPlayer")
                .containsEntry(JwtHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE,
                        NOW.plusSeconds(300));
    }

    @Test
    void rejectsHandshakeWithoutJwtSubprotocol() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws"));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "gameio.v1");
        when(request.getHeaders()).thenReturn(headers);

        boolean allowed = new JwtHandshakeInterceptor(mock(JwtDecoder.class), mock(UserRepository.class),
                mock(RealtimeRateLimiter.class), CLOCK)
                .beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(allowed).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsHandshakeFloodBeforeDecodingCredentials() {
        RealtimeRateLimiter limiter = mock(RealtimeRateLimiter.class);
        doThrow(new RealtimeRateLimitException()).when(limiter).checkHandshake("unknown");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean allowed = new JwtHandshakeInterceptor(mock(JwtDecoder.class), mock(UserRepository.class),
                limiter, CLOCK).beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(allowed).isFalse();
        verify(response).setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    }
}
