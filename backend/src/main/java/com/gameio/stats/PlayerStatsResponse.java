package com.gameio.stats;

import java.util.List;

public record PlayerStatsResponse(
        StatsSummaryResponse summary,
        List<GameStatsResponse> games,
        List<DailyActivityResponse> activity,
        ScoreTrendResponse scoreTrend,
        AchievementStatsResponse achievements,
        String mostPlayedGameSlug,
        String strongestMultiplayerGameSlug
) {
}
