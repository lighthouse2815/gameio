package com.gameio.achievement;

import com.gameio.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "player_achievements")
public class PlayerAchievement {
    @EmbeddedId
    private PlayerAchievementId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @MapsId("achievementId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id")
    private Achievement achievement;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    protected PlayerAchievement() {
    }

    public PlayerAchievement(UserAccount user, Achievement achievement, Instant unlockedAt) {
        this.id = new PlayerAchievementId(user.getId(), achievement.getId());
        this.user = user;
        this.achievement = achievement;
        this.unlockedAt = unlockedAt;
    }

    public Achievement getAchievement() {
        return achievement;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }
}
