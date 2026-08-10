package com.gameio.achievement;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AchievementRepository extends JpaRepository<Achievement, UUID> {
    List<Achievement> findByCodeIn(Collection<String> codes);

    List<Achievement> findAllByOrderByNameAsc();
}
