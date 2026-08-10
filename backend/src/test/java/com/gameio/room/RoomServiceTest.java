package com.gameio.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.game.Game;
import com.gameio.game.GameRepository;
import com.gameio.game.GameType;
import com.gameio.user.UserAccount;
import com.gameio.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RoomServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void readyDoesNotAutoStartAndOnlyOwnerCanExplicitlyStart() {
        RoomStore store = mock(RoomStore.class);
        GameRepository games = mock(GameRepository.class);
        UserRepository users = mock(UserRepository.class);
        RoomEventSink events = mock(RoomEventSink.class);
        UUID gameId = UUID.randomUUID();
        Game game = multiplayerGame(gameId);
        UserAccount owner = UserAccount.create("Owner", "owner@example.com", "hash", NOW);
        UserAccount guest = UserAccount.create("Guest", "guest@example.com", "hash", NOW);
        AtomicReference<RoomState> persisted = new AtomicReference<>();

        when(games.findById(gameId)).thenReturn(Optional.of(game));
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(users.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(store.findByCode(any())).thenReturn(Optional.empty());
        when(store.findById(any())).thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        doAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return null;
        }).when(store).save(any(RoomState.class));

        RoomService service = new RoomService(store, games, users, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        RoomState created = service.createForMatchmaking(gameId, List.of(owner.getId(), guest.getId()));
        service.reconnect(owner.getId(), created.roomId().toString());
        service.reconnect(guest.getId(), created.roomId().toString());

        service.ready(owner.getId(), created.roomId());
        RoomResponse bothReady = service.ready(guest.getId(), created.roomId());
        assertThat(bothReady.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(bothReady.players()).allMatch(RoomPlayer::ready);

        assertThatThrownBy(() -> service.start(guest.getId(), created.roomId()))
                .isInstanceOfSatisfying(InvalidRoomActionException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ROOM_OWNER_REQUIRED"));

        RoomResponse started = service.start(owner.getId(), created.roomId());
        assertThat(started.status()).isEqualTo(RoomStatus.PLAYING);
        verify(events).gameStarted(persisted.get());
    }

    @Test
    void rejectsCreatingOrJoiningAnotherActiveRoom() {
        RoomStore store = mock(RoomStore.class);
        GameRepository games = mock(GameRepository.class);
        UserRepository users = mock(UserRepository.class);
        UUID gameId = UUID.randomUUID();
        UserAccount user = UserAccount.create("RoomBound", "bound@example.com", "hash", NOW);
        RoomState active = room(UUID.randomUUID(), gameId, user);
        RoomState target = room(UUID.randomUUID(), gameId,
                UserAccount.create("OtherOwner", "other@example.com", "hash", NOW));
        Game game = multiplayerGame(gameId);
        when(games.findById(gameId)).thenReturn(Optional.of(game));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(store.findAll()).thenReturn(List.of(active, target));
        when(store.findById(target.roomId())).thenReturn(Optional.of(target));
        RoomService service = new RoomService(store, games, users, mock(RoomEventSink.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.create(user.getId(), new CreateRoomRequest(gameId, 2, true)))
                .isInstanceOfSatisfying(InvalidRoomActionException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ALREADY_IN_ANOTHER_ROOM"));
        assertThatThrownBy(() -> service.join(user.getId(), target.roomId().toString()))
                .isInstanceOfSatisfying(InvalidRoomActionException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ALREADY_IN_ANOTHER_ROOM"));
    }

    @Test
    void doesNotRebindAPastMemberToAFinishedRoom() {
        RoomStore store = mock(RoomStore.class);
        GameRepository games = mock(GameRepository.class);
        UserRepository users = mock(UserRepository.class);
        UUID gameId = UUID.randomUUID();
        UserAccount owner = UserAccount.create("FinishedOwner", "finished@example.com", "hash", NOW);
        RoomState finished = room(UUID.randomUUID(), gameId, owner).finished();
        when(store.findById(finished.roomId())).thenReturn(Optional.of(finished));
        when(store.findAll()).thenReturn(List.of(finished));
        RoomService service = new RoomService(store, games, users, mock(RoomEventSink.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.join(owner.getId(), finished.roomId().toString()))
                .isInstanceOfSatisfying(InvalidRoomActionException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ROOM_FINISHED"));
    }

    private Game multiplayerGame(UUID gameId) {
        Game game = mock(Game.class);
        when(game.getId()).thenReturn(gameId);
        when(game.getSlug()).thenReturn("tic-tac-toe");
        when(game.getName()).thenReturn("Tic Tac Toe");
        when(game.getGameType()).thenReturn(GameType.TURN_BASED_MULTIPLAYER);
        when(game.getMinPlayers()).thenReturn(2);
        when(game.getMaxPlayers()).thenReturn(2);
        when(game.isEnabled()).thenReturn(true);
        return game;
    }

    private RoomState room(UUID roomId, UUID gameId, UserAccount owner) {
        return new RoomState(roomId, "ABC234", gameId, "tic-tac-toe", "Tic Tac Toe", owner.getId(),
                2, 2, true, RoomStatus.WAITING,
                List.of(new RoomPlayer(owner.getId(), owner.getUsername(), false, true, true)),
                NOW, NOW.plusSeconds(21_600));
    }
}
