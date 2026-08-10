package com.gameio.leaderboard;

import java.util.List;

public record LeaderboardResponse(
        List<LeaderboardEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
