package com.gameio.multiplayer;

import jakarta.validation.constraints.NotNull;

public record QuickReactionRequest(@NotNull QuickReaction reaction) {
}
