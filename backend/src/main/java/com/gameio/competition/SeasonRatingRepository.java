package com.gameio.competition;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface SeasonRatingRepository extends JpaRepository<SeasonRating, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SeasonRating> findBySeasonIdAndUserIdAndGameId(UUID seasonId, UUID userId, UUID gameId);

    @EntityGraph(attributePaths = {"user", "game"})
    Page<SeasonRating> findBySeasonIdAndGameIdOrderByRatingDescGamesPlayedDescUpdatedAtAsc(
            UUID seasonId, UUID gameId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "game"})
    List<SeasonRating> findBySeasonIdAndUserIdOrderByRatingDesc(UUID seasonId, UUID userId);
}
