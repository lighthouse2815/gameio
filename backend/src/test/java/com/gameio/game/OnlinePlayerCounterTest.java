package com.gameio.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gameio.multiplayer.RealtimeSessionRegistry;
import com.gameio.room.RoomPlayer;
import com.gameio.room.RoomState;
import com.gameio.room.RoomStatus;
import com.gameio.room.RoomStore;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class OnlinePlayerCounterTest {
    @Test
    void countsDistinctPlayersWithAnActualSocketBinding() {
        RoomStore rooms = mock(RoomStore.class);
        RealtimeSessionRegistry sessions = mock(RealtimeSessionRegistry.class);
        UUID gameId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID connected = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        RoomState room = room(roomId, gameId, connected, stale);
        when(rooms.findAll()).thenReturn(List.of(room));
        when(sessions.hasRoomConnections(connected, roomId)).thenReturn(true);
        when(sessions.hasRoomConnections(stale, roomId)).thenReturn(false);

        assertThat(new OnlinePlayerCounter(rooms, sessions).count(Set.of(gameId)))
                .containsEntry(gameId, 1L);
    }

    @Test
    void returnsZeroMetricsWhenRedisIsUnavailable() {
        RoomStore rooms = mock(RoomStore.class);
        when(rooms.findAll()).thenThrow(new DataAccessResourceFailureException("Redis offline"));

        assertThat(new OnlinePlayerCounter(rooms, mock(RealtimeSessionRegistry.class))
                .count(Set.of(UUID.randomUUID()))).isEmpty();
    }

    private RoomState room(UUID roomId, UUID gameId, UUID connected, UUID stale) {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        return new RoomState(roomId, "ABC234", gameId, "tic-tac-toe", "Tic Tac Toe", connected,
                2, 2, false, RoomStatus.PLAYING,
                List.of(new RoomPlayer(connected, "Connected", true, true, true),
                        new RoomPlayer(stale, "Stale", true, false, true)),
                now, now.plusSeconds(21_600));
    }
}
