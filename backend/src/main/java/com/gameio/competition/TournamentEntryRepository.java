package com.gameio.competition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface TournamentEntryRepository extends JpaRepository<TournamentEntry, UUID> {
    long countByTournamentId(UUID tournamentId);
    boolean existsByTournamentIdAndUserId(UUID tournamentId, UUID userId);
    Optional<TournamentEntry> findByTournamentIdAndUserId(UUID tournamentId, UUID userId);

    @EntityGraph(attributePaths = "user")
    List<TournamentEntry> findByTournamentIdOrderBySeedNumberAsc(UUID tournamentId);
}
