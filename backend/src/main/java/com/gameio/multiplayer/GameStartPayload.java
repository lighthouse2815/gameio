package com.gameio.multiplayer;

import com.gameio.room.RoomPlayer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameStartPayload(
        UUID matchId,
        UUID gameId,
        String gameSlug,
        List<RoomPlayer> players,
        Instant startedAt,
        Object state
) {
}
