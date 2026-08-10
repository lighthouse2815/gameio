package com.gameio.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.game.GameRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaderboardServiceTest {
    @Test
    void servesGlobalPageFromCacheWithoutQueryingPostgres() {
        LeaderboardQueryRepository queries = mock(LeaderboardQueryRepository.class);
        LeaderboardCache cache = mock(LeaderboardCache.class);
        LeaderboardResponse cached = response();
        when(cache.getGlobal(0, 20)).thenReturn(Optional.of(cached));

        LeaderboardResponse result = new LeaderboardService(queries, mock(GameRepository.class), cache)
                .global(0, 20);

        assertThat(result).isSameAs(cached);
        verify(queries, never()).global(0, 20);
    }

    @Test
    void cachesDatabaseMissAndValidatesGameBeforeServingGamePage() {
        LeaderboardQueryRepository queries = mock(LeaderboardQueryRepository.class);
        GameRepository games = mock(GameRepository.class);
        LeaderboardCache cache = mock(LeaderboardCache.class);
        UUID gameId = UUID.randomUUID();
        LeaderboardResponse database = response();
        when(games.existsById(gameId)).thenReturn(true);
        when(cache.getForGame(gameId, 1, 10)).thenReturn(Optional.empty());
        when(queries.forGame(gameId, 1, 10)).thenReturn(database);

        LeaderboardResponse result = new LeaderboardService(queries, games, cache)
                .forGame(gameId, 1, 10);

        assertThat(result).isSameAs(database);
        verify(cache).putForGame(gameId, 1, 10, database);
    }

    private LeaderboardResponse response() {
        return new LeaderboardResponse(List.of(), 0, 20, 0, 0);
    }
}
