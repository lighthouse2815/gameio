package com.gameio.room;

import java.util.UUID;

public interface RoomEventSink {
    void roomUpdated(RoomState room);

    void gameStarted(RoomState room);

    void playerDisconnected(RoomState room, UUID userId);
}
