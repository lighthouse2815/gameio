package com.gameio.game;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<Game, UUID> {
    Optional<Game> findBySlugAndEnabledTrue(String slug);

    @Query("select game from Game game where game.enabled = true "
            + "and (:category is null or game.category = :category) "
            + "and (:gameType is null or game.gameType = :gameType)")
    Page<Game> findEnabled(
            @Param("category") GameCategory category,
            @Param("gameType") GameType gameType,
            Pageable pageable);

    @Query("select game from Game game where game.enabled = true "
            + "and (lower(game.name) like lower(concat('%', :search, '%')) "
            + "or lower(game.description) like lower(concat('%', :search, '%'))) "
            + "and (:category is null or game.category = :category) "
            + "and (:gameType is null or game.gameType = :gameType)")
    Page<Game> searchEnabled(
            @Param("search") String search,
            @Param("category") GameCategory category,
            @Param("gameType") GameType gameType,
            Pageable pageable);
}
