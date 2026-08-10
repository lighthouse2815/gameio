package com.gameio.leaderboard;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
class NoOpLeaderboardCache implements LeaderboardCache {
    @Override
    public Optional<LeaderboardResponse> getGlobal(int page, int size) {
        return Optional.empty();
    }

    @Override
    public void putGlobal(int page, int size, LeaderboardResponse response) {
    }

    @Override
    public Optional<LeaderboardResponse> getForGame(UUID gameId, int page, int size) {
        return Optional.empty();
    }

    @Override
    public void putForGame(UUID gameId, int page, int size, LeaderboardResponse response) {
    }

    @Override
    public void invalidate(UUID gameId) {
    }
}
