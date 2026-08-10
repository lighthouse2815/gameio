package com.gameio.multiplayer;

import java.util.UUID;

public record OpponentDisconnectedPayload(UUID userId, int reconnectGraceSeconds) {
}
