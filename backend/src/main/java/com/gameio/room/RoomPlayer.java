package com.gameio.room;

import java.util.UUID;

public record RoomPlayer(UUID id, String username, boolean ready, boolean owner, boolean connected) {
    public RoomPlayer readyUp() {
        return ready ? this : new RoomPlayer(id, username, true, owner, connected);
    }

    public RoomPlayer withOwner(boolean newOwner) {
        return new RoomPlayer(id, username, ready, newOwner, connected);
    }

    public RoomPlayer withConnection(boolean newConnection) {
        return new RoomPlayer(id, username, ready, owner, newConnection);
    }
}
