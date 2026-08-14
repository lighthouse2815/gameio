package com.gameio.competition;

import java.time.Instant;
import java.util.UUID;

public record RatingEntryResponse(
        long rank,
        UUID userId,
        String username,
        String avatarUrl,
        UUID gameId,
        String gameSlug,
        int rating,
        int gamesPlayed,
        int wins,
        int losses,
        int draws,
        Instant updatedAt
) {
    static RatingEntryResponse from(SeasonRating rating, long rank) {
        return new RatingEntryResponse(rank, rating.userId(), rating.username(), rating.avatarUrl(),
                rating.gameId(), rating.gameSlug(), rating.rating(), rating.gamesPlayed(), rating.wins(),
                rating.losses(), rating.draws(), rating.updatedAt());
    }
}
