package com.gameio.multiplayer;

import java.util.UUID;

public interface RealtimePublisher {
    void toUser(UUID userId, String type, UUID roomId, Object payload, String requestId);

    void toRoom(UUID roomId, String type, Object payload, String requestId);
}
