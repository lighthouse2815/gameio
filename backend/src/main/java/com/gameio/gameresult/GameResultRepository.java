package com.gameio.gameresult;

import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameResultRepository extends JpaRepository<GameResult, UUID> {
    boolean existsByMatchId(UUID matchId);

    long countByPlayerId(UUID playerId);

    long countByPlayerIdAndGameId(UUID playerId, UUID gameId);

    long countByPlayerIdAndResult(UUID playerId, GameResultType result);

    @Query("select new com.gameio.gameresult.GamePlayCount(result.game.id, count(result)) "
            + "from GameResult result where result.game.id in :gameIds group by result.game.id")
    List<GamePlayCount> countPlaysByGameIds(@Param("gameIds") Collection<UUID> gameIds);

    @Query("select coalesce(max(result.score), 0) from GameResult result "
            + "where result.player.id = :playerId and result.game.slug = :gameSlug")
    long maximumScore(@Param("playerId") UUID playerId, @Param("gameSlug") String gameSlug);

    @Query("select count(result) from GameResult result where result.player.id = :playerId "
            + "and result.game.slug = :gameSlug and result.result = 'WIN'")
    long countWins(@Param("playerId") UUID playerId, @Param("gameSlug") String gameSlug);

    @EntityGraph(attributePaths = {"game", "player", "session"})
    Page<GameResult> findByPlayerIdOrderByPlayedAtDesc(UUID playerId, Pageable pageable);

    @Query("select result.game.id as gameId, result.game.slug as gameSlug, result.game.name as gameName, "
            + "count(result) as gamesPlayed, "
            + "sum(case when result.result = 'WIN' then 1 else 0 end) as wins, "
            + "sum(case when result.result = 'LOSS' then 1 else 0 end) as losses, "
            + "sum(case when result.result = 'DRAW' then 1 else 0 end) as draws, "
            + "sum(case when result.result = 'COMPLETED' then 1 else 0 end) as completed, "
            + "sum(result.score) as totalScore, avg(result.score) as averageScore, max(result.score) as bestScore, "
            + "sum(result.durationSeconds) as totalDurationSeconds, max(result.playedAt) as lastPlayedAt "
            + "from GameResult result where result.player.id = :playerId "
            + "group by result.game.id, result.game.slug, result.game.name order by count(result) desc")
    List<GameStatsProjection> statisticsByGame(@Param("playerId") UUID playerId);

    List<GameResult> findByPlayerIdAndPlayedAtGreaterThanEqualOrderByPlayedAtAsc(
            UUID playerId, Instant playedAt);
}
