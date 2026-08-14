package com.gameio.multiplayer.invite;

import java.time.Instant;
import java.util.UUID;

public record GameInvite(
        UUID inviteId,
        UUID roomId,
        String roomCode,
        UUID gameId,
        String gameSlug,
        String gameName,
        UUID senderId,
        String senderUsername,
        UUID recipientId,
        String recipientUsername,
        Instant expiresAt
) {
}
