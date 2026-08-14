package com.gameio.multiplayer.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.matchmaking.MatchmakingService;
import com.gameio.multiplayer.RealtimeGameCoordinator;
import com.gameio.multiplayer.RealtimeSessionRegistry;
import com.gameio.multiplayer.invite.GameInviteService;
import com.gameio.room.RoomResponse;
import com.gameio.room.RoomService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class GameWebSocketHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void negotiatesOnlyPublicApplicationProtocolAndNeverEchoesJwtProtocol() {
        GameWebSocketHandler handler = new GameWebSocketHandler(JsonMapper.builder().build(),
                mock(RealtimeSessionRegistry.class), mock(RoomService.class), mock(MatchmakingService.class),
                mock(RealtimeGameCoordinator.class), mock(GameInviteService.class),
                mock(RealtimeRateLimiter.class), CLOCK);

        org.assertj.core.api.Assertions.assertThat(handler.getSubProtocols())
                .containsExactly("gameio.v1");
    }

    @Test
    void rejectsUnsupportedAuthoritativeFieldsBeforeDispatchingInput() {
        RealtimeSessionRegistry sessions = mock(RealtimeSessionRegistry.class);
        RoomService rooms = mock(RoomService.class);
        MatchmakingService matchmaking = mock(MatchmakingService.class);
        RealtimeGameCoordinator coordinator = mock(RealtimeGameCoordinator.class);
        GameWebSocketHandler handler = new GameWebSocketHandler(
                JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(),
                sessions, rooms, matchmaking, coordinator, mock(GameInviteService.class),
                mock(RealtimeRateLimiter.class), CLOCK);
        WebSocketSession session = mock(WebSocketSession.class);
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE, userId);
        attributes.put(JwtHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE, NOW.plusSeconds(300));
        when(session.getId()).thenReturn("session-1");
        when(session.getAttributes()).thenReturn(attributes);
        TextMessage message = new TextMessage("""
                {"type":"GAME_INPUT","requestId":"request-1","roomId":"%s",
                 "payload":{"action":"MOVE_UP","sequence":1,"hp":100,"x":50}}
                """.formatted(roomId));

        handler.handleTextMessage(session, message);

        verify(sessions).toSession(eq("session-1"), eq("ERROR"), isNull(), any(), eq("request-1"));
        verify(coordinator, never()).input(any(), any(), any(), any());
    }

    @Test
    void readyAndStartEchoCorrelatedRoomStateToCommandingSession() {
        RealtimeSessionRegistry sessions = mock(RealtimeSessionRegistry.class);
        RoomService rooms = mock(RoomService.class);
        GameWebSocketHandler handler = new GameWebSocketHandler(JsonMapper.builder().build(), sessions, rooms,
                mock(MatchmakingService.class), mock(RealtimeGameCoordinator.class),
                mock(GameInviteService.class), mock(RealtimeRateLimiter.class), CLOCK);
        WebSocketSession session = mock(WebSocketSession.class);
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomResponse readyState = mock(RoomResponse.class);
        RoomResponse playingState = mock(RoomResponse.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE, userId);
        attributes.put(JwtHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE, NOW.plusSeconds(300));
        when(session.getId()).thenReturn("session-2");
        when(session.getAttributes()).thenReturn(attributes);
        when(rooms.ready(userId, roomId)).thenReturn(readyState);
        when(rooms.start(userId, roomId)).thenReturn(playingState);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"ROOM_READY","requestId":"ready-1","roomId":"%s"}
                """.formatted(roomId)));
        handler.handleTextMessage(session, new TextMessage("""
                {"type":"ROOM_START","requestId":"start-1","roomId":"%s"}
                """.formatted(roomId)));

        verify(sessions).toSession("session-2", "ROOM_STATE", roomId, readyState, "ready-1");
        verify(sessions).toSession("session-2", "ROOM_STATE", roomId, playingState, "start-1");
    }

    @Test
    void closesSocketAndRejectsMessageAfterJwtExpiry() throws Exception {
        RealtimeSessionRegistry sessions = mock(RealtimeSessionRegistry.class);
        RealtimeGameCoordinator coordinator = mock(RealtimeGameCoordinator.class);
        GameWebSocketHandler handler = new GameWebSocketHandler(JsonMapper.builder().build(), sessions,
                mock(RoomService.class), mock(MatchmakingService.class), coordinator,
                mock(GameInviteService.class), mock(RealtimeRateLimiter.class), CLOCK);
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE, UUID.randomUUID());
        attributes.put(JwtHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE, NOW);
        when(session.getId()).thenReturn("expired-session");
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ROOM_READY\"}"));

        verify(sessions).toSession(eq("expired-session"), eq("ERROR"), isNull(), any(), isNull());
        verify(session).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        verify(coordinator, never()).input(any(), any(), any(), any());
    }

    @Test
    void explicitLeaveClearsEveryTabBoundToTheRoom() {
        RealtimeSessionRegistry sessions = mock(RealtimeSessionRegistry.class);
        RoomService rooms = mock(RoomService.class);
        RealtimeRateLimiter rateLimiter = mock(RealtimeRateLimiter.class);
        GameWebSocketHandler handler = new GameWebSocketHandler(JsonMapper.builder().build(), sessions, rooms,
                mock(MatchmakingService.class), mock(RealtimeGameCoordinator.class),
                mock(GameInviteService.class), rateLimiter, CLOCK);
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        WebSocketSession session = session("leave-session", userId, NOW.plusSeconds(300));

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"ROOM_LEAVE","requestId":"leave-1","roomId":"%s"}
                """.formatted(roomId)));

        verify(sessions).requireBoundRoom("leave-session", roomId);
        verify(rooms).leave(userId, roomId);
        verify(sessions).clearUserRoomBindings(userId, roomId);
        verify(sessions).toUser(eq(userId), eq("ROOM_LEFT"), eq(roomId), any(), eq("leave-1"));
    }

    @Test
    void closingOneTabDoesNotDisconnectPlayerWhileAnotherTabIsBoundToSameRoom() {
        RealtimeSessionRegistry sessions = mock(RealtimeSessionRegistry.class);
        RoomService rooms = mock(RoomService.class);
        GameWebSocketHandler handler = new GameWebSocketHandler(JsonMapper.builder().build(), sessions, rooms,
                mock(MatchmakingService.class), mock(RealtimeGameCoordinator.class),
                mock(GameInviteService.class), mock(RealtimeRateLimiter.class), CLOCK);
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("closed-tab");
        when(sessions.unregister("closed-tab"))
                .thenReturn(new RealtimeSessionRegistry.ConnectionInfo(userId, "Player", roomId));
        when(sessions.hasRoomConnections(userId, roomId)).thenReturn(true);

        handler.afterConnectionClosed(session, org.springframework.web.socket.CloseStatus.NORMAL);

        verify(rooms, never()).leave(userId, roomId);
    }

    private WebSocketSession session(String id, UUID userId, Instant expiresAt) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE, userId);
        attributes.put(JwtHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE, expiresAt);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
