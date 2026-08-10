package com.gameio.multiplayer;

import com.gameio.multiplayer.presence.PresenceStore;
import com.gameio.multiplayer.protocol.ServerEnvelope;
import com.gameio.room.InvalidRoomActionException;
import com.gameio.room.RoomState;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Primary
@Component
public class RealtimeSessionRegistry implements RealtimePublisher {
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final PresenceStore presence;
    private final Clock clock;

    public RealtimeSessionRegistry(ObjectMapper objectMapper, PresenceStore presence, Clock clock) {
        this.objectMapper = objectMapper;
        this.presence = presence;
        this.clock = clock;
    }

    public void register(WebSocketSession session, UUID userId, String username, Instant tokenExpiresAt) {
        if (tokenExpiresAt == null || !tokenExpiresAt.isAfter(Instant.now(clock))) {
            throw new IllegalArgumentException("WebSocket access token is expired");
        }
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, 5_000, 1_048_576);
        connections.put(session.getId(),
                new Connection(userId, username, decorated, tokenExpiresAt, null, null, null));
        userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session.getId());
        refreshPresenceFor(userId);
    }

    public ConnectionInfo unregister(String sessionId) {
        Connection removed = connections.remove(sessionId);
        if (removed == null) return null;
        Set<String> remainingSessions = userSessions.get(removed.userId());
        if (remainingSessions != null) {
            remainingSessions.remove(sessionId);
            if (remainingSessions.isEmpty()) {
                userSessions.remove(removed.userId(), remainingSessions);
            }
        }
        refreshPresenceFor(removed.userId());
        return removed.info();
    }

    public void bindRoom(String sessionId, RoomState room) {
        Connection existing = connections.get(sessionId);
        if (existing == null) {
            throw new InvalidRoomActionException("SOCKET_NOT_CONNECTED", "WebSocket session is not connected");
        }
        requireRoomAvailable(existing.userId(), room.roomId());
        connections.computeIfPresent(sessionId, (ignored, connection) -> {
            Connection bound = connection.withRoom(room.roomId(), room.gameSlug(), room.gameName());
            return bound;
        });
        refreshPresenceFor(existing.userId());
    }

    public void requireRoomAvailable(UUID userId, UUID roomId) {
        UUID otherRoomId = activeRoomId(userId);
        if (otherRoomId != null && !otherRoomId.equals(roomId)) {
            throw new InvalidRoomActionException("ALREADY_IN_ANOTHER_ROOM",
                    "Leave the active room before joining another room");
        }
    }

    public void unbindRoom(String sessionId) {
        Connection existing = connections.get(sessionId);
        if (existing == null) return;
        connections.computeIfPresent(sessionId, (ignored, connection) -> connection.withRoom(null, null, null));
        refreshPresenceFor(existing.userId());
    }

    public void unbindRoom(String sessionId, UUID roomId) {
        requireBoundRoom(sessionId, roomId);
        unbindRoom(sessionId);
    }

    public void clearRoomBindings(UUID roomId) {
        Set<UUID> affectedUsers = ConcurrentHashMap.newKeySet();
        connections.forEach((sessionId, ignored) -> connections.computeIfPresent(sessionId,
                (key, connection) -> {
                    if (!roomId.equals(connection.roomId())) return connection;
                    affectedUsers.add(connection.userId());
                    return connection.withRoom(null, null, null);
                }));
        affectedUsers.forEach(this::refreshPresenceFor);
    }

    public void clearUserRoomBindings(UUID userId, UUID roomId) {
        userSessions.getOrDefault(userId, Set.of()).forEach(sessionId -> connections.computeIfPresent(sessionId,
                (key, connection) -> roomId.equals(connection.roomId())
                        ? connection.withRoom(null, null, null)
                        : connection));
        refreshPresenceFor(userId);
    }

    public boolean hasConnections(UUID userId) {
        Instant now = Instant.now(clock);
        return userSessions.getOrDefault(userId, Set.of()).stream()
                .map(connections::get)
                .filter(java.util.Objects::nonNull)
                .anyMatch(connection -> connection.tokenExpiresAt().isAfter(now));
    }

    public boolean hasRoomConnections(UUID userId, UUID roomId) {
        Instant now = Instant.now(clock);
        return userSessions.getOrDefault(userId, Set.of()).stream()
                .map(connections::get)
                .filter(java.util.Objects::nonNull)
                .anyMatch(connection -> connection.tokenExpiresAt().isAfter(now)
                        && roomId.equals(connection.roomId()));
    }

    public void requireBoundRoom(String sessionId, UUID roomId) {
        Connection connection = connections.get(sessionId);
        if (connection == null || !roomId.equals(connection.roomId())) {
            throw new InvalidRoomActionException("ROOM_SOCKET_BINDING_REQUIRED",
                    "Join this room on the current WebSocket before sending room commands");
        }
    }

    public void toSession(String sessionId, String type, UUID roomId, Object payload, String requestId) {
        Connection connection = connections.get(sessionId);
        if (connection != null) {
            send(connection.session(), type, roomId, payload, requestId);
        }
    }

    @Override
    public void toUser(UUID userId, String type, UUID roomId, Object payload, String requestId) {
        Set<String> sessionIds = userSessions.getOrDefault(userId, Set.of());
        sessionIds.stream().map(connections::get).filter(java.util.Objects::nonNull)
                .forEach(connection -> send(connection.session(), type, roomId, payload, requestId));
    }

    @Override
    public void toRoom(UUID roomId, String type, Object payload, String requestId) {
        connections.values().stream().filter(connection -> roomId.equals(connection.roomId()))
                .forEach(connection -> send(connection.session(), type, roomId, payload, requestId));
    }

    @Scheduled(fixedDelay = 30_000)
    void refreshPresence() {
        Instant now = Instant.now(clock);
        userSessions.keySet().forEach(userId -> refreshPresenceFor(userId, now));
    }

    @Scheduled(fixedDelay = 5_000)
    void enforceTokenExpiry() {
        Instant now = Instant.now(clock);
        connections.values().stream()
                .filter(connection -> !connection.tokenExpiresAt().isAfter(now))
                .filter(connection -> connection.session().isOpen())
                .forEach(connection -> {
                    try {
                        connection.session().close(CloseStatus.POLICY_VIOLATION);
                    } catch (IOException ignored) {
                    }
                });
    }

    private void send(WebSocketSession session, String type, UUID roomId, Object payload, String requestId) {
        if (!session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(
                    new ServerEnvelope(type, requestId, roomId, payload, Instant.now()));
            session.sendMessage(new TextMessage(json));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize WebSocket event", exception);
        } catch (IOException exception) {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }
    }

    private UUID activeRoomId(UUID userId) {
        return userSessions.getOrDefault(userId, Set.of()).stream()
                .map(connections::get)
                .filter(java.util.Objects::nonNull)
                .map(Connection::roomId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void refreshPresenceFor(UUID userId) {
        refreshPresenceFor(userId, Instant.now(clock));
    }

    private void refreshPresenceFor(UUID userId, Instant now) {
        Connection preferred = userSessions.getOrDefault(userId, Set.of()).stream()
                .map(connections::get)
                .filter(java.util.Objects::nonNull)
                .filter(connection -> connection.tokenExpiresAt().isAfter(now))
                .reduce(null, (current, candidate) -> current == null || candidate.roomId() != null
                        ? candidate : current);
        if (preferred == null) {
            presence.offline(userId);
        } else {
            presence.online(userId, preferred.roomId(), preferred.gameSlug(), preferred.gameName());
        }
    }

    private record Connection(
            UUID userId,
            String username,
            WebSocketSession session,
            Instant tokenExpiresAt,
            UUID roomId,
            String gameSlug,
            String gameName
    ) {
        private Connection withRoom(UUID newRoomId, String newGameSlug, String newGameName) {
            return new Connection(userId, username, session, tokenExpiresAt,
                    newRoomId, newGameSlug, newGameName);
        }

        private ConnectionInfo info() {
            return new ConnectionInfo(userId, username, roomId);
        }
    }

    public record ConnectionInfo(UUID userId, String username, UUID roomId) {
    }
}
