package com.gameio.gameresult.multiplayer;

import com.gameio.achievement.UnlockedAchievementResponse;
import com.gameio.gameresult.GameResultType;
import java.util.List;
import java.util.UUID;

public record PlayerProgression(
        UUID userId,
        GameResultType result,
        long score,
        long expAwarded,
        int level,
        int ratingBefore,
        int ratingAfter,
        int ratingDelta,
        List<UnlockedAchievementResponse> unlockedAchievements
) {
}
