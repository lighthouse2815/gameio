package com.gameio.competition;

import java.time.Instant;
import java.util.UUID;

public record TournamentSummaryResponse(
        UUID id,
        String name,
        UUID gameId,
        String gameSlug,
        String gameName,
        UUID createdById,
        String createdByUsername,
        TournamentStatus status,
        int maxPlayers,
        long joinedPlayers,
        int currentRound,
        UUID winnerId,
        String winnerUsername,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    static TournamentSummaryResponse from(Tournament tournament, long joinedPlayers) {
        return new TournamentSummaryResponse(tournament.id(), tournament.name(), tournament.game().getId(),
                tournament.game().getSlug(), tournament.game().getName(), tournament.createdBy().getId(),
                tournament.createdBy().getUsername(), tournament.status(), tournament.maxPlayers(), joinedPlayers,
                tournament.currentRound(), tournament.winner() == null ? null : tournament.winner().getId(),
                tournament.winner() == null ? null : tournament.winner().getUsername(), tournament.createdAt(),
                tournament.startedAt(), tournament.completedAt());
    }
}
