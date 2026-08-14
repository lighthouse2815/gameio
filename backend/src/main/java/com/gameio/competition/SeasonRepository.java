package com.gameio.competition;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SeasonRepository extends JpaRepository<Season, UUID> {
    Optional<Season> findFirstByStartsAtLessThanEqualAndEndsAtGreaterThanOrderByStartsAtDesc(
            Instant startsAt, Instant endsAt);
}
