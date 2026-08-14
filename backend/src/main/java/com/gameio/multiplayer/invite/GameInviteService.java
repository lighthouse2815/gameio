package com.gameio.multiplayer.invite;

import com.gameio.friend.FriendService;
import com.gameio.multiplayer.RealtimePublisher;
import com.gameio.multiplayer.RealtimeSessionRegistry;
import com.gameio.room.InvalidRoomActionException;
import com.gameio.room.RoomService;
import com.gameio.room.RoomState;
import com.gameio.user.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GameInviteService {
    private static final Duration INVITE_TTL = Duration.ofSeconds(60);

    private final GameInviteStore invites;
    private final FriendService friends;
    private final RoomService rooms;
    private final RealtimeSessionRegistry sessions;
    private final RealtimePublisher realtime;
    private final Clock clock;

    public GameInviteService(
            GameInviteStore invites,
            FriendService friends,
            RoomService rooms,
            RealtimeSessionRegistry sessions,
            RealtimePublisher realtime,
            Clock clock) {
        this.invites = invites;
        this.friends = friends;
        this.rooms = rooms;
        this.sessions = sessions;
        this.realtime = realtime;
        this.clock = clock;
    }

    public GameInvitePayload send(UUID senderId, String senderUsername, UUID roomId, GameInviteRequest request) {
        if (request == null || request.recipientUsername() == null
                || !request.recipientUsername().matches("[A-Za-z0-9_]{3,30}")) {
            throw new InvalidRoomActionException("INVALID_INVITE_RECIPIENT", "Invite recipient is invalid");
        }
        RoomState room = rooms.requireInviteableRoom(senderId, roomId);
        UserAccount recipient = friends.requireAcceptedFriend(senderId, request.recipientUsername());
        if (room.hasPlayer(recipient.getId())) {
            throw new InvalidRoomActionException("PLAYER_ALREADY_IN_ROOM", "Friend is already in this room");
        }
        if (!sessions.hasConnections(recipient.getId())) {
            throw new InvalidRoomActionException("FRIEND_OFFLINE", "Friend must be online to receive an invite");
        }
        Instant expiresAt = Instant.now(clock).plus(INVITE_TTL);
        GameInvite invite = new GameInvite(UUID.randomUUID(), room.roomId(), room.roomCode(), room.gameId(),
                room.gameSlug(), room.gameName(), senderId, senderUsername, recipient.getId(),
                recipient.getUsername(), expiresAt);
        invites.save(invite, INVITE_TTL);
        GameInvitePayload payload = GameInvitePayload.from(invite);
        realtime.toUser(recipient.getId(), "GAME_INVITE", room.roomId(), payload, null);
        return payload;
    }

    public GameInvite accept(UUID recipientId, UUID inviteId) {
        GameInvite invite = consumeFor(recipientId, inviteId);
        friends.requireAcceptedFriend(recipientId, invite.senderId());
        rooms.requireInviteableRoom(invite.senderId(), invite.roomId());
        return invite;
    }

    public GameInvite decline(UUID recipientId, UUID inviteId) {
        return consumeFor(recipientId, inviteId);
    }

    private GameInvite consumeFor(UUID recipientId, UUID inviteId) {
        if (inviteId == null) {
            throw new InvalidRoomActionException("INVITE_ID_REQUIRED", "Invite identifier is required");
        }
        GameInvite invite = invites.consume(inviteId)
                .orElseThrow(() -> new InvalidRoomActionException("INVITE_EXPIRED", "Game invite expired"));
        if (!invite.recipientId().equals(recipientId)) {
            throw new InvalidRoomActionException("INVITE_RECIPIENT_MISMATCH",
                    "Game invite belongs to another player");
        }
        if (!invite.expiresAt().isAfter(Instant.now(clock))) {
            throw new InvalidRoomActionException("INVITE_EXPIRED", "Game invite expired");
        }
        return invite;
    }
}
