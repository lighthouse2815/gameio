package com.gameio.game;

import com.gameio.multiplayer.RealtimeSessionRegistry;
import com.gameio.room.RoomState;
import com.gameio.room.RoomStatus;
import com.gameio.room.RoomStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class OnlinePlayerCounter {
    private static final Logger log = LoggerFactory.getLogger(OnlinePlayerCounter.class);
    private final RoomStore rooms;
    private final RealtimeSessionRegistry sessions;

    OnlinePlayerCounter(RoomStore rooms, RealtimeSessionRegistry sessions) {
        this.rooms = rooms;
        this.sessions = sessions;
    }

    Map<UUID, Long> count(Set<UUID> gameIds) {
        if (gameIds.isEmpty()) return Map.of();
        Map<UUID, Set<UUID>> connectedByGame = new HashMap<>();
        try {
            for (RoomState room : rooms.findAll()) {
                if (!gameIds.contains(room.gameId()) || room.status() == RoomStatus.FINISHED) continue;
                Set<UUID> connected = connectedByGame.computeIfAbsent(room.gameId(), ignored -> new HashSet<>());
                room.players().stream()
                        .filter(com.gameio.room.RoomPlayer::connected)
                        .map(com.gameio.room.RoomPlayer::id)
                        .filter(userId -> sessions.hasRoomConnections(userId, room.roomId()))
                        .forEach(connected::add);
            }
        } catch (RuntimeException exception) {
            log.warn("Online player count is unavailable because Redis could not be read");
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        connectedByGame.forEach((gameId, players) -> counts.put(gameId, (long) players.size()));
        return Map.copyOf(counts);
    }
}
