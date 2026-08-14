package com.gameio.stats;

import com.gameio.gameresult.GameStatsProjection;
import java.time.Instant;
import java.util.UUID;

public record GameStatsResponse(
        UUID gameId,
        String gameSlug,
        String gameName,
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
        Instant lastPlayedAt
) {
    static GameStatsResponse from(GameStatsProjection row) {
        double winRate = row.getWins() + row.getLosses() + row.getDraws() == 0
                ? 0
                : row.getWins() * 100.0 / (row.getWins() + row.getLosses() + row.getDraws());
        return new GameStatsResponse(row.getGameId(), row.getGameSlug(), row.getGameName(), row.getGamesPlayed(),
                row.getWins(), row.getLosses(), row.getDraws(), row.getCompleted(), row.getTotalScore(),
                row.getBestScore(), row.getAverageScore(), row.getTotalDurationSeconds(), winRate,
                row.getLastPlayedAt());
    }
}
