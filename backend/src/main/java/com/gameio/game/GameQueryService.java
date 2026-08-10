package com.gameio.game;

import com.gameio.common.web.PageResponse;
import com.gameio.gameresult.GamePlayCount;
import com.gameio.gameresult.GameResultRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameQueryService {
    private final GameRepository games;
    private final GameResultRepository results;
    private final OnlinePlayerCounter onlinePlayers;

    public GameQueryService(
            GameRepository games, GameResultRepository results, OnlinePlayerCounter onlinePlayers) {
        this.games = games;
        this.results = results;
        this.onlinePlayers = onlinePlayers;
    }

    @Transactional(readOnly = true)
    public PageResponse<GameResponse> search(
            String search, GameCategory category, GameType gameType, int page, int size) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Game> resultPage = normalizedSearch == null
                ? games.findEnabled(category, gameType, pageable)
                : games.searchEnabled(normalizedSearch, category, gameType, pageable);
        Map<UUID, GameMetrics> metrics = metrics(resultPage.getContent());
        return PageResponse.from(resultPage, game -> response(game, metrics));
    }

    @Transactional(readOnly = true)
    public GameResponse findBySlug(String slug) {
        Game game = games.findBySlugAndEnabledTrue(slug).orElseThrow(GameNotFoundException::new);
        return response(game, metrics(java.util.List.of(game)));
    }

    private Map<UUID, GameMetrics> metrics(java.util.List<Game> gameList) {
        Set<UUID> gameIds = gameList.stream().map(Game::getId).collect(Collectors.toUnmodifiableSet());
        if (gameIds.isEmpty()) return Map.of();
        Map<UUID, Long> playCounts = results.countPlaysByGameIds(gameIds).stream()
                .collect(Collectors.toUnmodifiableMap(GamePlayCount::gameId, GamePlayCount::playsCount));
        Map<UUID, Long> connectedCounts = onlinePlayers.count(gameIds);
        return gameIds.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), gameId ->
                new GameMetrics(connectedCounts.getOrDefault(gameId, 0L), playCounts.getOrDefault(gameId, 0L))));
    }

    private GameResponse response(Game game, Map<UUID, GameMetrics> metrics) {
        GameMetrics gameMetrics = metrics.getOrDefault(game.getId(), GameMetrics.EMPTY);
        return GameResponse.from(game, gameMetrics.onlinePlayers, gameMetrics.playsCount);
    }

    private record GameMetrics(long onlinePlayers, long playsCount) {
        private static final GameMetrics EMPTY = new GameMetrics(0, 0);
    }
}
