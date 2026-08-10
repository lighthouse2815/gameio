package com.gameio.matchmaking;

import java.time.Instant;
import java.util.UUID;

public record MatchmakingTicketResponse(
        UUID ticketId,
        UUID gameId,
        MatchmakingStatus status,
        UUID roomId,
        Instant joinedAt
) {
    static MatchmakingTicketResponse from(MatchmakingTicket ticket) {
        return new MatchmakingTicketResponse(ticket.ticketId(), ticket.gameId(), ticket.status(),
                ticket.roomId(), ticket.joinedAt());
    }
}
