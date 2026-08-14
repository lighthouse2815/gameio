package com.gameio.multiplayer.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.friend.FriendService;
import com.gameio.multiplayer.RealtimePublisher;
import com.gameio.multiplayer.RealtimeSessionRegistry;
import com.gameio.room.InvalidRoomActionException;
import com.gameio.room.RoomPlayer;
import com.gameio.room.RoomService;
import com.gameio.room.RoomState;
import com.gameio.room.RoomStatus;
import com.gameio.user.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GameInviteServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void sendsSingleUseInviteOnlyToAnOnlineAcceptedFriend() {
        GameInviteStore store = mock(GameInviteStore.class);
        FriendService friends = mock(FriendService.class);
        RoomService rooms = mock(RoomService.class);
        RealtimeSessionRegistry sessions = mock(RealtimeSessionRegistry.class);
        RealtimePublisher realtime = mock(RealtimePublisher.class);
        UUID senderId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        UserAccount recipient = UserAccount.create("Recipient", "recipient@example.com", "hash", NOW);
        RoomState room = room(roomId, gameId, senderId);
        when(rooms.requireInviteableRoom(senderId, roomId)).thenReturn(room);
        when(friends.requireAcceptedFriend(senderId, "Recipient")).thenReturn(recipient);
        when(sessions.hasConnections(recipient.getId())).thenReturn(true);

        GameInviteService service = new GameInviteService(store, friends, rooms, sessions, realtime, CLOCK);
        GameInvitePayload payload = service.send(senderId, "Sender", roomId,
                new GameInviteRequest("Recipient"));

        assertThat(payload.roomId()).isEqualTo(roomId);
        assertThat(payload.recipientId()).isEqualTo(recipient.getId());
        assertThat(payload.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        ArgumentCaptor<GameInvite> invite = ArgumentCaptor.forClass(GameInvite.class);
        verify(store).save(invite.capture(), eq(Duration.ofSeconds(60)));
        assertThat(invite.getValue().recipientUsername()).isEqualTo("Recipient");
        verify(realtime).toUser(recipient.getId(), "GAME_INVITE", roomId, payload, null);
    }

    @Test
    void consumesInviteAndRevalidatesFriendshipAndWaitingRoomOnAccept() {
        GameInviteStore store = mock(GameInviteStore.class);
        FriendService friends = mock(FriendService.class);
        RoomService rooms = mock(RoomService.class);
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        GameInvite invite = invite(senderId, recipientId, roomId, NOW.plusSeconds(60));
        when(store.consume(invite.inviteId())).thenReturn(Optional.of(invite));
        when(rooms.requireInviteableRoom(senderId, roomId))
                .thenReturn(room(roomId, invite.gameId(), senderId));
        GameInviteService service = new GameInviteService(store, friends, rooms,
                mock(RealtimeSessionRegistry.class), mock(RealtimePublisher.class), CLOCK);

        assertThat(service.accept(recipientId, invite.inviteId())).isEqualTo(invite);
        verify(friends).requireAcceptedFriend(recipientId, senderId);
        verify(rooms).requireInviteableRoom(senderId, roomId);
    }

    @Test
    void rejectsConsumedInviteForAnotherRecipient() {
        GameInviteStore store = mock(GameInviteStore.class);
        GameInvite invite = invite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(60));
        when(store.consume(invite.inviteId())).thenReturn(Optional.of(invite));
        GameInviteService service = new GameInviteService(store, mock(FriendService.class),
                mock(RoomService.class), mock(RealtimeSessionRegistry.class),
                mock(RealtimePublisher.class), CLOCK);

        assertThatThrownBy(() -> service.decline(UUID.randomUUID(), invite.inviteId()))
                .isInstanceOfSatisfying(InvalidRoomActionException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVITE_RECIPIENT_MISMATCH"));
    }

    private RoomState room(UUID roomId, UUID gameId, UUID senderId) {
        return new RoomState(roomId, "ABC234", gameId, "tic-tac-toe", "Tic Tac Toe", senderId,
                2, 2, true, RoomStatus.WAITING,
                List.of(new RoomPlayer(senderId, "Sender", true, true, true)),
                NOW, NOW.plusSeconds(21_600));
    }

    private GameInvite invite(UUID senderId, UUID recipientId, UUID roomId, Instant expiresAt) {
        return new GameInvite(UUID.randomUUID(), roomId, "ABC234", UUID.randomUUID(), "tic-tac-toe",
                "Tic Tac Toe", senderId, "Sender", recipientId, "Recipient", expiresAt);
    }
}
