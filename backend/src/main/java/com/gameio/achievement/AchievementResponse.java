package com.gameio.achievement;

import java.util.UUID;

public record AchievementResponse(
        UUID id,
        String code,
        String name,
        String description,
        String icon,
        int expReward
) {
    static AchievementResponse from(Achievement achievement) {
        return new AchievementResponse(achievement.getId(), achievement.getCode(), achievement.getName(),
                achievement.getDescription(), achievement.getIcon(), achievement.getExpReward());
    }
}
