package com.gameio.multiplayer.engine;

import com.gameio.gameresult.GameResultType;
import java.util.UUID;

public record EngineOutcome(UUID userId, GameResultType result, long score) {
}
