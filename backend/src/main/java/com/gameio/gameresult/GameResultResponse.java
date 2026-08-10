package com.gameio.gameresult;

import com.gameio.achievement.UnlockedAchievementResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameResultResponse(
        UUID id,
        UUID sessionId,
        UUID matchId,
        UUID gameId,
        String gameSlug,
        String gameName,
        String username,
        long score,
        GameResultType result,
        int durationSeconds,
        Instant playedAt,
        long expAwarded,
        int resultingLevel,
        List<UnlockedAchievementResponse> unlockedAchievements
) {
    static GameResultResponse completed(
            GameResult result, long expAwarded, int resultingLevel,
            List<UnlockedAchievementResponse> unlockedAchievements) {
        UUID sessionId = result.getSession() == null ? null : result.getSession().getId();
        return new GameResultResponse(result.getId(), sessionId, result.getMatchId(), result.getGame().getId(),
                result.getGame().getSlug(), result.getGame().getName(), result.getPlayer().getUsername(),
                result.getScore(), result.getResult(),
                result.getDurationSeconds(), result.getPlayedAt(), expAwarded, resultingLevel, unlockedAchievements);
    }

    static GameResultResponse history(GameResult result) {
        return completed(result, 0, result.getPlayer().getLevel(), List.of());
    }
}
