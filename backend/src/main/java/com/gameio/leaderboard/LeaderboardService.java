package com.gameio.leaderboard;

import com.gameio.game.GameNotFoundException;
import com.gameio.game.GameRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaderboardService {
    private final LeaderboardQueryRepository leaderboardQueries;
    private final GameRepository games;
    private final LeaderboardCache cache;

    public LeaderboardService(
            LeaderboardQueryRepository leaderboardQueries, GameRepository games, LeaderboardCache cache) {
        this.leaderboardQueries = leaderboardQueries;
        this.games = games;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse global(int page, int size) {
        return cache.getGlobal(page, size).orElseGet(() -> {
            LeaderboardResponse response = leaderboardQueries.global(page, size);
            cache.putGlobal(page, size, response);
            return response;
        });
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse forGame(UUID gameId, int page, int size) {
        if (!games.existsById(gameId)) {
            throw new GameNotFoundException();
        }
        return cache.getForGame(gameId, page, size).orElseGet(() -> {
            LeaderboardResponse response = leaderboardQueries.forGame(gameId, page, size);
            cache.putForGame(gameId, page, size, response);
            return response;
        });
    }
}
