package com.gameio.achievement;

import java.time.Instant;
import java.util.UUID;

public record UnlockedAchievementResponse(
        UUID id,
        String code,
        String name,
        String description,
        String icon,
        int expReward,
        Instant unlockedAt
) {
    static UnlockedAchievementResponse from(PlayerAchievement playerAchievement) {
        Achievement achievement = playerAchievement.getAchievement();
        return new UnlockedAchievementResponse(achievement.getId(), achievement.getCode(), achievement.getName(),
                achievement.getDescription(), achievement.getIcon(), achievement.getExpReward(),
                playerAchievement.getUnlockedAt());
    }
}
