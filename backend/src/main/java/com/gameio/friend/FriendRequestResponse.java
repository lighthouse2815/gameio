package com.gameio.friend;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FriendRequestResponse(
        UUID id,
        FriendResponse sender,
        FriendResponse recipient,
        FriendshipStatus status,
        Instant createdAt
) {
    static FriendRequestResponse from(Friendship friendship, Map<UUID, FriendPresence> presenceByUser) {
        return new FriendRequestResponse(
                friendship.getId(),
                FriendResponse.from(friendship.sender(), presenceByUser.get(friendship.sender().getId())),
                FriendResponse.from(friendship.recipient(), presenceByUser.get(friendship.recipient().getId())),
                friendship.getStatus(),
                friendship.getCreatedAt());
    }
}
