package com.gameio.achievement;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PlayerAchievementId implements Serializable {
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "achievement_id")
    private UUID achievementId;

    protected PlayerAchievementId() {
    }

    public PlayerAchievementId(UUID userId, UUID achievementId) {
        this.userId = userId;
        this.achievementId = achievementId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAchievementId() {
        return achievementId;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PlayerAchievementId id
                && Objects.equals(userId, id.userId) && Objects.equals(achievementId, id.achievementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, achievementId);
    }
}
