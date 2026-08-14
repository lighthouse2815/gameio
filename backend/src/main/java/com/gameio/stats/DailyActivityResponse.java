package com.gameio.stats;

import java.time.LocalDate;

public record DailyActivityResponse(
        LocalDate date,
        long gamesPlayed,
        long wins,
        long score,
        long durationSeconds
) {
}
