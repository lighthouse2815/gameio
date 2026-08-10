package com.gameio.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameio.common.web.PageResponse;
import com.gameio.gameresult.GamePlayCount;
import com.gameio.gameresult.GameResultRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class GameQueryServiceTest {
    @Test
    void enrichesCatalogWithPersistedPlayCountAndLivePlayerCount() {
        GameRepository games = mock(GameRepository.class);
        GameResultRepository results = mock(GameResultRepository.class);
        OnlinePlayerCounter onlinePlayers = mock(OnlinePlayerCounter.class);
        Game game = game();
        UUID gameId = game.getId();
        when(games.findEnabled(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(game)));
        when(results.countPlaysByGameIds(Set.of(gameId)))
                .thenReturn(List.of(new GamePlayCount(gameId, 41)));
        when(onlinePlayers.count(Set.of(gameId))).thenReturn(Map.of(gameId, 3L));

        PageResponse<GameResponse> response = new GameQueryService(games, results, onlinePlayers)
                .search(null, null, null, 0, 20);

        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.slug()).isEqualTo("tic-tac-toe");
            assertThat(item.onlinePlayers()).isEqualTo(3);
            assertThat(item.playsCount()).isEqualTo(41);
        });
        verify(results).countPlaysByGameIds(Set.of(gameId));
    }

    private Game game() {
        Game game = mock(Game.class);
        when(game.getId()).thenReturn(UUID.randomUUID());
        when(game.getName()).thenReturn("Tic Tac Toe");
        when(game.getSlug()).thenReturn("tic-tac-toe");
        when(game.getDescription()).thenReturn("Server-authoritative grid game");
        when(game.getCategory()).thenReturn(GameCategory.STRATEGY);
        when(game.getGameType()).thenReturn(GameType.TURN_BASED_MULTIPLAYER);
        when(game.getMinPlayers()).thenReturn(2);
        when(game.getMaxPlayers()).thenReturn(2);
        when(game.getCreatedAt()).thenReturn(Instant.parse("2026-08-10T00:00:00Z"));
        return game;
    }
}
