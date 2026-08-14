package com.gameio.stats;

public record ScoreTrendResponse(
        double recentSevenDayAverage,
        double previousSevenDayAverage,
        Double percentChange
) {
}
