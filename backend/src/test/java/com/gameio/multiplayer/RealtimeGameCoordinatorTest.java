package com.gameio.multiplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.gameresult.GameResultType;
import com.gameio.gameresult.multiplayer.AuthoritativeMatchResult;
import com.gameio.gameresult.multiplayer.AuthoritativeResultService;
import com.gameio.multiplayer.engine.AuthoritativeEngine;
import com.gameio.multiplayer.engine.EngineRegistry;
import com.gameio.multiplayer.presence.PresenceStore;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        when(engine.snapshot()).thenReturn(Map.of("sequence", 0));
        when(results.record(any())).thenReturn(List.of());
        RealtimeGameCoordinator coordinator = new RealtimeGameCoordinator(
                engines, realtime, rooms, results, presence, sessions, clock);
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
        when(engine.snapshot()).thenReturn(Map.of("sequence", 0));
        when(results.record(any())).thenReturn(List.of());
        RealtimeGameCoordinator coordinator = new RealtimeGameCoordinator(engines, mock(RealtimePublisher.class),
                mock(RoomStore.class), results, mock(PresenceStore.class),
                mock(RealtimeSessionRegistry.class), clock);
        coordinator.gameStarted(room);
        clock.set(startedAt.plus(RealtimeGameCoordinator.MAX_MATCH_DURATION).minusSeconds(1));
        assertThat(coordinator.reconnect(roomId, first)).isTrue();
        clock.set(startedAt.plus(RealtimeGameCoordinator.MAX_MATCH_DURATION).plusSeconds(1));

        coordinator.tick();

        verify(results).record(org.mockito.ArgumentMatchers.argThat(result -> result.outcomes().stream()
                .allMatch(outcome -> outcome.result() == GameResultType.DRAW)));
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
