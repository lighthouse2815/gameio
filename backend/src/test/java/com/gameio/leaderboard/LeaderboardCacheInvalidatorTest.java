package com.gameio.leaderboard;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaderboardCacheInvalidatorTest {
    @Test
    void invalidatesImmediatelyWhenNoTransactionIsActive() {
        LeaderboardCache cache = mock(LeaderboardCache.class);
        UUID gameId = UUID.randomUUID();

        new LeaderboardCacheInvalidator(cache).afterCommit(gameId);

        verify(cache).invalidate(gameId);
    }
}
