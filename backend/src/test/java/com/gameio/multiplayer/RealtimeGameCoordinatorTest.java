package com.gameio.multiplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.gameresult.GameResultType;
import com.gameio.gameresult.multiplayer.AuthoritativeMatchResult;
import com.gameio.gameresult.multiplayer.AuthoritativeResultService;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineRegistry;
import com.gameio.multiplayer.engine.tictactoe.TicTacToeEngineFactory;
import com.gameio.multiplayer.engine.tictactoe.TicTacToeSnapshot;
import com.gameio.multiplayer.presence.PresenceStore;
import com.gameio.observability.GameioOperationalMetrics;
import com.gameio.room.RoomPlayer;
import com.gameio.room.RoomState;
import com.gameio.room.RoomStatus;
import com.gameio.room.RoomStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

class RealtimeGameCoordinatorTest {
    @Test
    void idleMatchFinishesOnceAsAuthoritativeDrawAndReleasesBindings() {
        Instant startedAt = Instant.parse("2026-08-10T00:00:00Z");
        Instant expiredAt = startedAt.plus(RealtimeGameCoordinator.MAX_IDLE_DURATION).plusSeconds(1);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(startedAt, expiredAt, expiredAt, expiredAt);
        EngineRegistry engines = mock(EngineRegistry.class);
        AuthoritativeEngine engine = mock(AuthoritativeEngine.class);
        RealtimePublisher realtime = mock(RealtimePublisher.class);
        RoomStore rooms = mock(RoomStore.class);
        AuthoritativeResultService results = mock(AuthoritativeResultService.class);
        PresenceStore presence = mock(PresenceStore.class);
        RealtimeSessionRegistry sessions = mock(RealtimeSessionRegistry.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomState room = new RoomState(roomId, "ABC234", UUID.randomUUID(), "tic-tac-toe", "Tic Tac Toe",
                first, 2, 2, true, RoomStatus.PLAYING,
                List.of(new RoomPlayer(first, "First", true, true, true),
                        new RoomPlayer(second, "Second", true, false, true)),
                startedAt, startedAt.plusSeconds(21_600));
        when(engines.create(eq("tic-tac-toe"), any())).thenReturn(engine);
        when(engines.checkpoint(engine)).thenReturn(
                JsonMapper.builder().build().createObjectNode().put("sequence", 0));
        when(engine.snapshot()).thenReturn(Map.of("sequence", 0));
        when(results.record(any())).thenReturn(List.of());
        RealtimeGameCoordinator coordinator = new RealtimeGameCoordinator(
                engines, realtime, rooms, results, presence, sessions, mock(ActiveMatchStore.class),
                mock(ApplicationEventPublisher.class), mock(GameioOperationalMetrics.class), clock);
        coordinator.gameStarted(room);

        coordinator.tick();
        coordinator.tick();

        ArgumentCaptor<AuthoritativeMatchResult> resultCaptor = ArgumentCaptor.forClass(
                AuthoritativeMatchResult.class);
        verify(results, times(1)).record(resultCaptor.capture());
        assertThat(resultCaptor.getValue().outcomes())
                .allMatch(outcome -> outcome.result() == GameResultType.DRAW && outcome.score() == 0);
        verify(rooms).save(org.mockito.ArgumentMatchers.argThat(saved -> saved.status() == RoomStatus.FINISHED));
        verify(realtime).toRoom(eq(roomId), eq("GAME_OVER"), any(), any());
        verify(sessions).clearRoomBindings(roomId);
    }

    @Test
    void hardMatchDeadlineFinishesEvenWhenRecentActivityPreventsIdleTimeout() {
        Instant startedAt = Instant.parse("2026-08-10T00:00:00Z");
        MutableClock clock = new MutableClock(startedAt);
        EngineRegistry engines = mock(EngineRegistry.class);
        AuthoritativeEngine engine = mock(AuthoritativeEngine.class);
        AuthoritativeResultService results = mock(AuthoritativeResultService.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomState room = new RoomState(roomId, "ABC234", UUID.randomUUID(), "tic-tac-toe", "Tic Tac Toe",
                first, 2, 2, true, RoomStatus.PLAYING,
                List.of(new RoomPlayer(first, "First", true, true, true),
                        new RoomPlayer(second, "Second", true, false, true)),
                startedAt, startedAt.plusSeconds(21_600));
        when(engines.create(eq("tic-tac-toe"), any())).thenReturn(engine);
        when(engines.checkpoint(engine)).thenReturn(
                JsonMapper.builder().build().createObjectNode().put("sequence", 0));
        when(engine.snapshot()).thenReturn(Map.of("sequence", 0));
        when(results.record(any())).thenReturn(List.of());
        RealtimeGameCoordinator coordinator = new RealtimeGameCoordinator(engines, mock(RealtimePublisher.class),
                mock(RoomStore.class), results, mock(PresenceStore.class),
                mock(RealtimeSessionRegistry.class), mock(ActiveMatchStore.class),
                mock(ApplicationEventPublisher.class), mock(GameioOperationalMetrics.class), clock);
        coordinator.gameStarted(room);
        clock.set(startedAt.plus(RealtimeGameCoordinator.MAX_MATCH_DURATION).minusSeconds(1));
        assertThat(coordinator.reconnect(roomId, first)).isTrue();
        clock.set(startedAt.plus(RealtimeGameCoordinator.MAX_MATCH_DURATION).plusSeconds(1));

        coordinator.tick();

        verify(results).record(org.mockito.ArgumentMatchers.argThat(result -> result.outcomes().stream()
                .allMatch(outcome -> outcome.result() == GameResultType.DRAW)));
    }

    @Test
    void restoresCheckpointAfterCoordinatorRestartAndContinuesTheSameTurnSequence() {
        Instant startedAt = Instant.parse("2026-08-10T00:00:00Z");
        MutableClock clock = new MutableClock(startedAt);
        JsonMapper objectMapper = JsonMapper.builder().build();
        EngineRegistry engines = new EngineRegistry(List.of(new TicTacToeEngineFactory()), objectMapper);
        InMemoryActiveMatchStore checkpoints = new InMemoryActiveMatchStore();
        RoomStore rooms = mock(RoomStore.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomState room = new RoomState(roomId, "REST01", UUID.randomUUID(), "tic-tac-toe", "Tic Tac Toe",
                first, 2, 2, false, RoomStatus.PLAYING,
                List.of(new RoomPlayer(first, "First", true, true, true),
                        new RoomPlayer(second, "Second", true, false, true)),
                startedAt, startedAt.plusSeconds(21_600));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        RealtimeGameCoordinator original = new RealtimeGameCoordinator(engines, mock(RealtimePublisher.class),
                rooms, mock(AuthoritativeResultService.class), mock(PresenceStore.class),
                mock(RealtimeSessionRegistry.class), checkpoints, mock(ApplicationEventPublisher.class),
                mock(GameioOperationalMetrics.class), clock);
        original.gameStarted(room);
        original.input(roomId, first, new com.gameio.multiplayer.engine.GameInput(
                "PLACE_PIECE", 0, 0, null), "move-1");

        RealtimePublisher restoredPublisher = mock(RealtimePublisher.class);
        RealtimeSessionRegistry restoredSessions = mock(RealtimeSessionRegistry.class);
        when(restoredSessions.hasRoomConnections(first, roomId)).thenReturn(true);
        RealtimeGameCoordinator restored = new RealtimeGameCoordinator(engines, restoredPublisher, rooms,
                mock(AuthoritativeResultService.class), mock(PresenceStore.class), restoredSessions,
                checkpoints, mock(ApplicationEventPublisher.class), mock(GameioOperationalMetrics.class), clock);

        assertThat(restored.reconnect(roomId, first)).isTrue();
        ArgumentCaptor<Object> snapshot = ArgumentCaptor.forClass(Object.class);
        verify(restoredPublisher).toUser(eq(first), eq("GAME_STATE"), eq(roomId), snapshot.capture(), isNull());
        TicTacToeSnapshot recovered = (TicTacToeSnapshot) snapshot.getValue();
        assertThat(recovered.sequence()).isEqualTo(1);
        assertThat(recovered.board().getFirst().getFirst()).isEqualTo("X");
        assertThat(recovered.currentTurnPlayerId()).isEqualTo(second);

        restored.input(roomId, second, new com.gameio.multiplayer.engine.GameInput(
                "PLACE_PIECE", 1, 1, null), "move-2");
        verify(restoredPublisher).toRoom(eq(roomId), eq("GAME_STATE"),
                org.mockito.ArgumentMatchers.argThat(state -> state instanceof TicTacToeSnapshot next
                        && next.sequence() == 2 && next.board().get(1).get(1).equals("O")), eq("move-2"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant newInstant) {
            instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
