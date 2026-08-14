package com.gameio.gamepreference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface GamePreferenceRepository extends JpaRepository<GamePreference, UUID> {
    @EntityGraph(attributePaths = "game")
    List<GamePreference> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<GamePreference> findByUserIdAndGameId(UUID userId, UUID gameId);
}
