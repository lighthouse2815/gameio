package com.gameio.multiplayer;

import java.util.UUID;

public record QuickReactionPayload(UUID userId, String username, QuickReaction reaction) {
}
