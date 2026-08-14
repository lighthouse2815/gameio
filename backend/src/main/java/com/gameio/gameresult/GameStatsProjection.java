package com.gameio.gameresult;

import java.time.Instant;
import java.util.UUID;

public interface GameStatsProjection {
    UUID getGameId();

    String getGameSlug();

    String getGameName();

    long getGamesPlayed();

    long getWins();

    long getLosses();

    long getDraws();

    long getCompleted();

    long getTotalScore();

    double getAverageScore();

    long getBestScore();

    long getTotalDurationSeconds();

    Instant getLastPlayedAt();
}
