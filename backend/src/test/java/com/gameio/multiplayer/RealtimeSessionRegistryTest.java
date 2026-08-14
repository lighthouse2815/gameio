package com.gameio.multiplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.multiplayer.presence.PresenceStore;
import com.gameio.observability.GameioOperationalMetrics;
import com.gameio.room.RoomPlayer;
import com.gameio.room.RoomState;
import com.gameio.room.RoomStatus;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class RealtimeSessionRegistryTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void refreshPrefersRoomBoundTabAndFinishClearsEveryRoomBinding() {
        PresenceStore presence = mock(PresenceStore.class);
        RealtimeSessionRegistry registry = new RealtimeSessionRegistry(JsonMapper.builder().build(), presence,
                Clock.fixed(NOW, ZoneOffset.UTC), mock(GameioOperationalMetrics.class));
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        WebSocketSession lobbySession = session("lobby");
        WebSocketSession gameSession = session("game");
        registry.register(lobbySession, userId, "Player", NOW.plusSeconds(300));
        registry.register(gameSession, userId, "Player", NOW.plusSeconds(300));
        RoomState room = new RoomState(roomId, "ABC234", UUID.randomUUID(), "tank-battle", "Tank Battle",
                userId, 2, 4, true, RoomStatus.PLAYING,
                List.of(new RoomPlayer(userId, "Player", true, true, true)),
                Instant.parse("2026-08-10T00:00:00Z"), Instant.parse("2026-08-10T06:00:00Z"));
        registry.bindRoom("game", room);
        clearInvocations(presence);

        registry.refreshPresence();

        verify(presence).online(userId, roomId, "tank-battle", "Tank Battle");

        registry.clearRoomBindings(roomId);
        RealtimeSessionRegistry.ConnectionInfo connection = registry.unregister("game");
        assertThat(connection.roomId()).isNull();
    }

    @Test
    void roomConnectionRemainsUntilLastBoundTabClosesAndExplicitLeaveClearsAllTabs() {
        RealtimeSessionRegistry registry = new RealtimeSessionRegistry(JsonMapper.builder().build(),
                mock(PresenceStore.class), Clock.fixed(NOW, ZoneOffset.UTC),
                mock(GameioOperationalMetrics.class));
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomState room = new RoomState(roomId, "ABC234", UUID.randomUUID(), "caro", "Caro",
                userId, 2, 2, true, RoomStatus.PLAYING,
                List.of(new RoomPlayer(userId, "Player", true, true, true)), NOW, NOW.plusSeconds(21_600));
        registry.register(session("first"), userId, "Player", NOW.plusSeconds(300));
        registry.register(session("second"), userId, "Player", NOW.plusSeconds(300));
        registry.register(session("lobby"), userId, "Player", NOW.plusSeconds(300));
        registry.bindRoom("first", room);
        registry.bindRoom("second", room);

        registry.unregister("first");
        assertThat(registry.hasRoomConnections(userId, roomId)).isTrue();

        registry.clearUserRoomBindings(userId, roomId);
        assertThat(registry.hasRoomConnections(userId, roomId)).isFalse();
        assertThat(registry.unregister("second").roomId()).isNull();
    }

    @Test
    void spectatorBindingCanShareRoomChannelWithoutClaimingPlayerMembership() {
        PresenceStore presence = mock(PresenceStore.class);
        RealtimeSessionRegistry registry = new RealtimeSessionRegistry(JsonMapper.builder().build(), presence,
                Clock.fixed(NOW, ZoneOffset.UTC), mock(GameioOperationalMetrics.class));
        UUID spectatorId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomState room = new RoomState(roomId, "WATCH1", UUID.randomUUID(), "caro", "Caro",
                UUID.randomUUID(), 2, 2, false, RoomStatus.PLAYING,
                List.of(new RoomPlayer(UUID.randomUUID(), "First", true, true, true),
                        new RoomPlayer(UUID.randomUUID(), "Second", true, false, true)),
                NOW, NOW.plusSeconds(21_600));
        registry.register(session("spectator"), spectatorId, "Viewer", NOW.plusSeconds(300));
        clearInvocations(presence);

        registry.bindSpectator("spectator", room);

        registry.requireRoomChannel("spectator", roomId);
        assertThat(registry.hasRoomConnections(spectatorId, roomId)).isFalse();
        assertThatThrownBy(() -> registry.requireBoundRoom("spectator", roomId))
                .isInstanceOf(com.gameio.room.InvalidRoomActionException.class);
        verify(presence).online(spectatorId, null, null, null);

        registry.unbindRoom("spectator", roomId);

        assertThatThrownBy(() -> registry.requireRoomChannel("spectator", roomId))
                .isInstanceOf(com.gameio.room.InvalidRoomActionException.class);
        RealtimeSessionRegistry.ConnectionInfo info = registry.unregister("spectator");
        assertThat(info.roomId()).isNull();
        assertThat(info.spectator()).isFalse();
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }
}
