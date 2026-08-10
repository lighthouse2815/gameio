package com.gameio.leaderboard;

import java.util.UUID;

public record LeaderboardEntry(
        long rank,
        UUID userId,
        String username,
        String avatarUrl,
        long score,
        long wins
) {
}
