package com.gameio.multiplayer.invite;

import java.time.Instant;
import java.util.UUID;

public record GameInvitePayload(
        UUID inviteId,
        UUID roomId,
        String roomCode,
        UUID gameId,
        String gameSlug,
        String gameName,
        String senderUsername,
        UUID recipientId,
        Instant expiresAt
) {
    public static GameInvitePayload from(GameInvite invite) {
        return new GameInvitePayload(invite.inviteId(), invite.roomId(), invite.roomCode(), invite.gameId(),
                invite.gameSlug(), invite.gameName(), invite.senderUsername(), invite.recipientId(),
                invite.expiresAt());
    }
}
