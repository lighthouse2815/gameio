package com.gameio.room;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoomResponse(
        UUID roomId,
        String roomCode,
        UUID gameId,
        String gameSlug,
        String gameName,
        UUID ownerId,
        int maxPlayers,
        boolean privateRoom,
        RoomStatus status,
        List<RoomPlayer> players,
        Instant createdAt
) {
    public static RoomResponse from(RoomState room) {
        return new RoomResponse(room.roomId(), room.roomCode(), room.gameId(), room.gameSlug(), room.gameName(),
                room.ownerId(), room.maxPlayers(), room.privateRoom(), room.status(), room.players(), room.createdAt());
    }
}
