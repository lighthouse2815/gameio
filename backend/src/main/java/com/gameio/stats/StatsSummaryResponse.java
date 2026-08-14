package com.gameio.stats;

public record StatsSummaryResponse(
        long gamesPlayed,
        long wins,
        long losses,
        long draws,
        long completed,
        long totalScore,
        long bestScore,
        double averageScore,
        long totalDurationSeconds,
        double winRate,
        long activeDays,
        long currentPlayStreak
) {
}
