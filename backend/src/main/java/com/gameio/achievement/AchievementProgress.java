package com.gameio.achievement;

public record AchievementProgress(
        long completedGames,
        long wins,
        long snakeBestScore,
        long ticTacToeWins
) {
}
