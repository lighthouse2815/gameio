package com.gameio.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.game.Game;
import com.gameio.game.GameRepository;
import com.gameio.game.GameType;
import com.gameio.multiplayer.RealtimePublisher;
import com.gameio.room.RoomPlayer;
import com.gameio.room.RoomService;
import com.gameio.room.RoomState;
import com.gameio.room.RoomStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchmakingServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void queuesFirstPlayerThenCreatesRoomForOldestCompleteGroup() {
        MatchmakingStore store = mock(MatchmakingStore.class);
        GameRepository games = mock(GameRepository.class);
        RoomService rooms = mock(RoomService.class);
        RealtimePublisher realtime = mock(RealtimePublisher.class);
        UUID gameId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Game game = mock(Game.class);
        List<MatchmakingTicket> queue = new ArrayList<>();

        when(game.isEnabled()).thenReturn(true);
        when(game.getGameType()).thenReturn(GameType.TURN_BASED_MULTIPLAYER);
        when(game.getMinPlayers()).thenReturn(2);
        when(games.findById(gameId)).thenReturn(Optional.of(game));
        when(store.findByUser(any())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            queue.add(invocation.getArgument(0));
            return null;
        }).when(store).enqueue(any(MatchmakingTicket.class));
        when(store.takeOldest(eq(gameId), eq(2))).thenAnswer(invocation -> {
            if (queue.size() < 2) return List.of();
            List<MatchmakingTicket> matched = List.copyOf(queue);
            queue.clear();
            return matched;
        });
        RoomState room = new RoomState(roomId, "ABC234", gameId, "tic-tac-toe", "Tic Tac Toe",
                first, 2, 2, true, RoomStatus.WAITING,
                List.of(new RoomPlayer(first, "First", false, true, true),
                        new RoomPlayer(second, "Second", false, false, true)),
                NOW, NOW.plusSeconds(3600));
        when(rooms.createForMatchmaking(eq(gameId), any())).thenReturn(room);

        MatchmakingService service = new MatchmakingService(store, games, rooms, realtime,
                Clock.fixed(NOW, ZoneOffset.UTC));
        MatchmakingTicketResponse waiting = service.join(first, gameId);
        MatchmakingTicketResponse matched = service.join(second, gameId);

        assertThat(waiting.status()).isEqualTo(MatchmakingStatus.QUEUED);
        assertThat(waiting.roomId()).isNull();
        assertThat(matched.status()).isEqualTo(MatchmakingStatus.MATCH_FOUND);
        assertThat(matched.roomId()).isEqualTo(roomId);
        verify(store, times(2)).saveMatched(any(MatchmakingTicket.class));
        verify(realtime, times(2)).toUser(any(), eq("MATCH_FOUND"), eq(roomId), any(), any());
    }
}
