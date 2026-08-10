package com.gameio.leaderboard;

import java.util.Optional;
import java.util.UUID;

interface LeaderboardCache {
    Optional<LeaderboardResponse> getGlobal(int page, int size);

    void putGlobal(int page, int size, LeaderboardResponse response);

    Optional<LeaderboardResponse> getForGame(UUID gameId, int page, int size);

    void putForGame(UUID gameId, int page, int size, LeaderboardResponse response);

    void invalidate(UUID gameId);
}
