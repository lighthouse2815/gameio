package com.gameio.competition;

import java.time.Instant;
import java.util.UUID;

public record TournamentPlayerResponse(
        UUID userId, String username, String avatarUrl, int seedNumber, boolean eliminated, Instant joinedAt
) {
    static TournamentPlayerResponse from(TournamentEntry entry) {
        return new TournamentPlayerResponse(entry.user().getId(), entry.user().getUsername(),
                entry.user().getAvatarUrl(), entry.seedNumber(), entry.eliminated(), entry.joinedAt());
    }
}
