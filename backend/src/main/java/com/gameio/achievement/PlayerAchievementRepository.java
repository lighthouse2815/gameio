package com.gameio.achievement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlayerAchievementRepository extends JpaRepository<PlayerAchievement, PlayerAchievementId> {
    boolean existsByIdUserIdAndIdAchievementId(UUID userId, UUID achievementId);

    @EntityGraph(attributePaths = "achievement")
    List<PlayerAchievement> findByIdUserIdOrderByUnlockedAtDesc(UUID userId);
}
