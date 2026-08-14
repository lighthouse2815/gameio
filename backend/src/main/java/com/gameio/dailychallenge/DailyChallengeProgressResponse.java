package com.gameio.dailychallenge;

import java.time.LocalDate;

public record DailyChallengeProgressResponse(
        LocalDate date,
        boolean completedToday,
        long todayBestScore,
        long completedDays,
        long currentStreak,
        long longestStreak,
        long distinctSoloGames
) {
}
