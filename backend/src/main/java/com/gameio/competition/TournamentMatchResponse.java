package com.gameio.competition;

import java.time.Instant;
import java.util.UUID;

public record TournamentMatchResponse(
        UUID id,
        int roundNumber,
        int bracketIndex,
        UUID playerOneId,
        String playerOneUsername,
        UUID playerTwoId,
        String playerTwoUsername,
        UUID winnerId,
        String winnerUsername,
        UUID roomId,
        TournamentMatchStatus status,
        Instant completedAt
) {
    static TournamentMatchResponse from(TournamentMatch match) {
        return new TournamentMatchResponse(match.id(), match.roundNumber(), match.bracketIndex(),
                match.playerOne().getId(), match.playerOne().getUsername(),
                match.playerTwo() == null ? null : match.playerTwo().getId(),
                match.playerTwo() == null ? null : match.playerTwo().getUsername(),
                match.winner() == null ? null : match.winner().getId(),
                match.winner() == null ? null : match.winner().getUsername(),
                match.roomId(), match.status(), match.completedAt());
    }
}
