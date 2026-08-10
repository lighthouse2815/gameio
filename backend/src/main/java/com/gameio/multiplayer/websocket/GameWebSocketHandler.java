package com.gameio.multiplayer.websocket;

import com.gameio.common.error.ApiException;
import com.gameio.matchmaking.JoinMatchmakingRequest;
import com.gameio.matchmaking.MatchmakingService;
import com.gameio.matchmaking.MatchmakingTicketResponse;
import com.gameio.multiplayer.RealtimeGameCoordinator;
import com.gameio.multiplayer.RealtimeRoomExpiredException;
import com.gameio.multiplayer.RealtimeSessionRegistry;
import com.gameio.multiplayer.RoomLeftPayload;
import com.gameio.multiplayer.engine.GameInput;
import com.gameio.multiplayer.protocol.ClientEnvelope;
import com.gameio.room.RoomResponse;
import com.gameio.room.RoomService;
import com.gameio.room.RoomState;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private static final String REQUEST_IDS_ATTRIBUTE = "gameio.requestIds";
    private static final int MAX_MESSAGE_BYTES = 16_384;

    private final ObjectMapper objectMapper;
    private final RealtimeSessionRegistry sessions;
    private final RoomService rooms;
    private final MatchmakingService matchmaking;
    private final RealtimeGameCoordinator coordinator;
    private final RealtimeRateLimiter rateLimiter;
    private final Clock clock;

    public GameWebSocketHandler(
            ObjectMapper objectMapper,
            RealtimeSessionRegistry sessions,
            RoomService rooms,
            MatchmakingService matchmaking,
            RealtimeGameCoordinator coordinator,
            RealtimeRateLimiter rateLimiter,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.sessions = sessions;
        this.rooms = rooms;
        this.matchmaking = matchmaking;
        this.coordinator = coordinator;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Override
    public List<String> getSubProtocols() {
        return List.of(JwtHandshakeInterceptor.APPLICATION_PROTOCOL);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = userId(session);
        String username = (String) session.getAttributes().get(JwtHandshakeInterceptor.USERNAME_ATTRIBUTE);
        Instant expiresAt = (Instant) session.getAttributes().get(JwtHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE);
        sessions.register(session, userId, username, expiresAt);
        sessions.toSession(session.getId(), "CONNECTED", null,
                new ConnectedPayload(userId, username), null);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientEnvelope envelope = null;
        try {
            log.debug("Received realtime WebSocket message for session {}", session.getId());
            if (tokenExpired(session)) {
                error(session, null, "ACCESS_TOKEN_EXPIRED", "WebSocket access token has expired");
                closePolicyViolation(session);
                return;
            }
            rateLimiter.checkMessage(userId(session), clientAddress(session));
            if (message.getPayloadLength() > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException("WebSocket message is too large");
            }
            envelope = objectMapper.readValue(message.getPayload(), ClientEnvelope.class);
            validateEnvelope(envelope);
            if (duplicateRequest(session, envelope.requestId())) return;
            dispatch(session, envelope);
        } catch (ApiException exception) {
            error(session, envelope == null ? null : envelope.requestId(), exception.code(), exception.getMessage());
            if (exception instanceof RealtimeRateLimitException) closePolicyViolation(session);
        } catch (JacksonException | IllegalArgumentException exception) {
            error(session, envelope == null ? null : envelope.requestId(), "INVALID_MESSAGE",
                    "WebSocket message is malformed or contains unsupported fields");
        } catch (RuntimeException exception) {
            log.warn("Realtime command failed for session {} and request {}", session.getId(),
                    envelope == null ? null : envelope.requestId(), exception);
            error(session, envelope == null ? null : envelope.requestId(), "REALTIME_ERROR",
                    "Realtime command could not be completed");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        RealtimeSessionRegistry.ConnectionInfo connection = sessions.unregister(session.getId());
        if (connection != null && connection.roomId() != null
                && !sessions.hasRoomConnections(connection.userId(), connection.roomId())) {
            try {
                rooms.leave(connection.userId(), connection.roomId());
            } catch (ApiException ignored) {
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    private void dispatch(WebSocketSession session, ClientEnvelope envelope) {
        UUID userId = userId(session);
        switch (envelope.type()) {
            case "ROOM_JOIN" -> joinRoom(session, userId, envelope);
            case "ROOM_LEAVE" -> {
                UUID roomId = roomId(envelope);
                sessions.requireBoundRoom(session.getId(), roomId);
                rooms.leave(userId, roomId);
                sessions.clearUserRoomBindings(userId, roomId);
                sessions.toUser(userId, "ROOM_LEFT", roomId, new RoomLeftPayload(userId), envelope.requestId());
            }
            case "ROOM_READY" -> {
                UUID roomId = roomId(envelope);
                sessions.requireBoundRoom(session.getId(), roomId);
                RoomResponse room = rooms.ready(userId, roomId);
                sessions.toSession(session.getId(), "ROOM_STATE", roomId, room, envelope.requestId());
            }
            case "ROOM_START" -> {
                UUID roomId = roomId(envelope);
                sessions.requireBoundRoom(session.getId(), roomId);
                RoomResponse room = rooms.start(userId, roomId);
                sessions.toSession(session.getId(), "ROOM_STATE", roomId, room, envelope.requestId());
            }
            case "MATCHMAKING_JOIN" -> {
                JoinMatchmakingRequest request = requirePayload(envelope, JoinMatchmakingRequest.class);
                MatchmakingTicketResponse ticket = matchmaking.join(userId, request.gameId());
                sessions.toSession(session.getId(), "MATCHMAKING_STATE", ticket.roomId(), ticket, envelope.requestId());
            }
            case "MATCHMAKING_LEAVE" -> matchmaking.leave(userId);
            case "GAME_INPUT" -> {
                rateLimiter.checkGameInput(userId, clientAddress(session));
                UUID roomId = roomId(envelope);
                sessions.requireBoundRoom(session.getId(), roomId);
                coordinator.input(roomId, userId,
                        requirePayload(envelope, GameInput.class), envelope.requestId());
            }
            default -> error(session, envelope.requestId(), "UNKNOWN_EVENT", "WebSocket event type is not supported");
        }
    }

    private void joinRoom(WebSocketSession session, UUID userId, ClientEnvelope envelope) {
        if (envelope.roomId() == null || envelope.roomId().isBlank()) {
            throw new IllegalArgumentException("roomId is required");
        }
        UUID targetRoomId = rooms.resolveRoomId(envelope.roomId());
        sessions.requireRoomAvailable(userId, targetRoomId);
        RoomResponse joined = rooms.join(userId, envelope.roomId());
        RoomState room = rooms.reconnect(userId, joined.roomId().toString());
        sessions.bindRoom(session.getId(), room);
        sessions.toSession(session.getId(), "ROOM_STATE", room.roomId(), RoomResponse.from(room), envelope.requestId());
        if (room.status() == com.gameio.room.RoomStatus.PLAYING && !coordinator.reconnect(room.roomId(), userId)) {
            rooms.expireUnrestorableMatch(room.roomId());
            sessions.clearRoomBindings(room.roomId());
            throw new RealtimeRoomExpiredException();
        }
    }

    private UUID roomId(ClientEnvelope envelope) {
        if (envelope.roomId() == null) throw new IllegalArgumentException("roomId is required");
        return UUID.fromString(envelope.roomId());
    }

    private <T> T requirePayload(ClientEnvelope envelope, Class<T> type) {
        if (envelope.payload() == null || envelope.payload().isNull()) {
            throw new IllegalArgumentException("payload is required");
        }
        return objectMapper.treeToValue(envelope.payload(), type);
    }

    private void validateEnvelope(ClientEnvelope envelope) {
        if (envelope.type() == null || !envelope.type().matches("[A-Z_]{3,40}")) {
            throw new IllegalArgumentException("event type is invalid");
        }
        if (envelope.requestId() != null && envelope.requestId().length() > 80) {
            throw new IllegalArgumentException("requestId is too long");
        }
    }

    @SuppressWarnings("unchecked")
    private boolean duplicateRequest(WebSocketSession session, String requestId) {
        if (requestId == null || requestId.isBlank()) return false;
        Set<String> ids = (Set<String>) session.getAttributes().computeIfAbsent(
                REQUEST_IDS_ATTRIBUTE, ignored -> java.util.Collections.synchronizedSet(new LinkedHashSet<>()));
        synchronized (ids) {
            if (ids.contains(requestId)) return true;
            if (ids.size() >= 256) ids.clear();
            ids.add(requestId);
            return false;
        }
    }

    private UUID userId(WebSocketSession session) {
        return (UUID) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
    }

    private String clientAddress(WebSocketSession session) {
        java.net.InetSocketAddress address = session.getRemoteAddress();
        if (address == null) return "unknown";
        return address.getAddress() == null ? address.getHostString() : address.getAddress().getHostAddress();
    }

    private boolean tokenExpired(WebSocketSession session) {
        Instant expiresAt = (Instant) session.getAttributes().get(JwtHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE);
        return expiresAt == null || !expiresAt.isAfter(Instant.now(clock));
    }

    private void closePolicyViolation(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
        }
    }

    private void error(WebSocketSession session, String requestId, String code, String message) {
        sessions.toSession(session.getId(), "ERROR", null, new ErrorPayload(code, message), requestId);
    }

    private record ConnectedPayload(UUID userId, String username) {
    }

    private record ErrorPayload(String code, String message) {
    }
}
