package com.gameio.user;

import com.gameio.achievement.UnlockedAchievementResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String username,
        String avatarUrl,
        int level,
        long exp,
        Instant createdAt,
        long gamesPlayed,
        long wins,
        List<UnlockedAchievementResponse> achievements
) {
}
