package com.gameio.game;

import java.time.Instant;
import java.util.UUID;

public record GameResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String thumbnailUrl,
        GameCategory category,
        GameType gameType,
        int minPlayers,
        int maxPlayers,
        Instant createdAt,
        long onlinePlayers,
        long playsCount
) {
    public static GameResponse from(Game game, long onlinePlayers, long playsCount) {
        return new GameResponse(game.getId(), game.getName(), game.getSlug(), game.getDescription(),
                game.getThumbnailUrl(), game.getCategory(), game.getGameType(), game.getMinPlayers(),
                game.getMaxPlayers(), game.getCreatedAt(), onlinePlayers, playsCount);
    }
}
