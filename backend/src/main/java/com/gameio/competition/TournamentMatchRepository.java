package com.gameio.competition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface TournamentMatchRepository extends JpaRepository<TournamentMatch, UUID> {
    @EntityGraph(attributePaths = {"tournament", "tournament.game", "playerOne", "playerTwo", "winner"})
    Optional<TournamentMatch> findByRoomId(UUID roomId);

    @EntityGraph(attributePaths = {"playerOne", "playerTwo", "winner"})
    List<TournamentMatch> findByTournamentIdOrderByRoundNumberAscBracketIndexAsc(UUID tournamentId);

    @EntityGraph(attributePaths = {"playerOne", "playerTwo", "winner"})
    List<TournamentMatch> findByTournamentIdAndRoundNumberOrderByBracketIndexAsc(
            UUID tournamentId, int roundNumber);
}
