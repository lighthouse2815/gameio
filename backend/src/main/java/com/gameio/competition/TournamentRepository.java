package com.gameio.competition;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface TournamentRepository extends JpaRepository<Tournament, UUID> {
    @EntityGraph(attributePaths = {"game", "createdBy", "winner"})
    Page<Tournament> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"game", "createdBy", "winner"})
    Optional<Tournament> findDetailedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"game", "createdBy", "winner"})
    Optional<Tournament> findForUpdateById(UUID id);
}
