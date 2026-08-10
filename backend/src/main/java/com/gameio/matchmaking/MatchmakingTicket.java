package com.gameio.matchmaking;

import java.time.Instant;
import java.util.UUID;

public record MatchmakingTicket(
        UUID ticketId,
        UUID userId,
        UUID gameId,
        MatchmakingStatus status,
        UUID roomId,
        Instant joinedAt,
        Instant expiresAt
) {
    public MatchmakingTicket matched(UUID matchedRoomId) {
        return new MatchmakingTicket(ticketId, userId, gameId, MatchmakingStatus.MATCH_FOUND,
                matchedRoomId, joinedAt, expiresAt);
    }
}
